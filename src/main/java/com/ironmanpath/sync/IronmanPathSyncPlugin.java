package com.ironmanpath.sync;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Ironman Path Sync.
 *
 * Sends the player's bank (plus inventory and worn items, optional) and real skill levels
 * to https://ironmanpath.app so the site can build the best gear set for a boss
 * from what the player actually owns.
 *
 * Linking: the site shows a 6-letter code. The plugin uses it ONCE; the server answers with a
 * private key (token) that the plugin stores. From then on the bank is sent every time it is
 * opened, or on demand with "Sync now" (last bank the client saw this session). "Unlink" deletes the key.
 *
 * Privacy / safety:
 *  - Only game data travels: display name, skill levels and item ids with quantities.
 *  - No password, e-mail, session token or account information is read or sent
 *    (RuneLite does not expose those to plugins anyway).
 */
@Slf4j
@PluginDescriptor(
	name = "Ironman Path Sync",
	description = "Send your bank and levels to ironmanpath.app (one-time code) to get the best gear set for a boss from what you own",
	tags = {"ironman", "bank", "gear", "dps", "sync"}
)
public class IronmanPathSyncPlugin extends Plugin
{
	static final String ENDPOINT = "https://ironmanpath.app/api/sync"; // sin www: Vercel redirige www -> apex y OkHttp no sigue 307 en POST
	private static final String VERSION = "1.1.0";
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final int SEND_DELAY_TICKS = 3; // wait ~1.8s after the last bank change before sending
	private static final int MIN_TICKS_BETWEEN_SENDS = 50; // ~30s between automatic sends

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private IronmanPathSyncConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private Gson gson;

	@Inject
	private ClientToolbar clientToolbar;

	private IronmanPathSyncPanel panel;
	private NavigationButton navButton;
	private int ticksUntilSend = -1;
	private int ticksSinceLastSend = MIN_TICKS_BETWEEN_SENDS;
	private boolean warnedNoCode = false;

	@Override
	protected void startUp()
	{
		ticksUntilSend = -1;
		panel = new IronmanPathSyncPanel(this);
		navButton = NavigationButton.builder()
			.tooltip("Ironman Path Sync")
			.icon(icon())
			.priority(9)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		log.debug("Ironman Path Sync started");
	}

	@Override
	protected void shutDown()
	{
		ticksUntilSend = -1;
		clientToolbar.removeNavigation(navButton);
		panel = null;
		log.debug("Ironman Path Sync stopped");
	}

	/* ---------- Estado ---------- */

	boolean isLinked()
	{
		String tok = config.token();
		return tok != null && tok.length() >= 32;
	}

	boolean hasCode()
	{
		String code = config.code();
		return code != null && code.trim().length() >= 4;
	}

	String linkedRsn()
	{
		String r = config.linkedRsn();
		return r == null || r.isEmpty() ? "?" : r;
	}

	/* ---------- Acciones del panel ---------- */

	void linkWithCode(String code)
	{
		String c = code == null ? "" : code.trim().toUpperCase();
		if (c.length() < 4)
		{
			panel.setResult("Write the 6-letter code from the website first.", false);
			return;
		}
		configManager.setConfiguration(IronmanPathSyncConfig.GROUP, IronmanPathSyncConfig.KEY_CODE, c);
		panel.setResult("Sending with code " + c + "...", true);
		clientThread.invokeLater(() -> sendSnapshot(true));
	}

	void syncNow()
	{
		if (!isLinked() && !hasCode())
		{
			panel.setResult("Link first (paste the code from the website).", false);
			return;
		}
		panel.setResult("Sending...", true);
		clientThread.invokeLater(() -> sendSnapshot(true));
	}

	void unlink()
	{
		configManager.unsetConfiguration(IronmanPathSyncConfig.GROUP, IronmanPathSyncConfig.KEY_TOKEN);
		configManager.unsetConfiguration(IronmanPathSyncConfig.GROUP, IronmanPathSyncConfig.KEY_RSN);
		configManager.unsetConfiguration(IronmanPathSyncConfig.GROUP, IronmanPathSyncConfig.KEY_CODE);
		panel.setResult("Unlinked. Generate a new code on the website to link again.", true);
		panel.refresh();
	}

	/* ---------- Eventos del juego ---------- */

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.BANK || !config.autoSend())
		{
			return;
		}
		if (!isLinked() && !hasCode())
		{
			if (!warnedNoCode)
			{
				warnedNoCode = true;
				chat("Ironman Path: not linked. Open the Ironman Path Sync side panel and paste the code from ironmanpath.app.");
			}
			return;
		}
		// The bank fires many change events while it loads; wait for it to settle.
		ticksUntilSend = SEND_DELAY_TICKS;
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (ticksSinceLastSend < MIN_TICKS_BETWEEN_SENDS)
		{
			ticksSinceLastSend++;
		}
		if (ticksUntilSend < 0)
		{
			return;
		}
		if (ticksUntilSend > 0)
		{
			ticksUntilSend--;
			return;
		}
		ticksUntilSend = -1;
		if (ticksSinceLastSend < MIN_TICKS_BETWEEN_SENDS)
		{
			ticksUntilSend = MIN_TICKS_BETWEEN_SENDS - ticksSinceLastSend;
			return;
		}
		sendSnapshot(false);
	}

	/* ---------- Envio ---------- */

	/** Builds the JSON snapshot on the client thread and posts it in the background. manual = from the panel. */
	private void sendSnapshot(boolean manual)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			if (manual)
			{
				panel.setResult("Log in to the game first.", false);
			}
			return;
		}
		Player local = client.getLocalPlayer();
		if (local == null || local.getName() == null)
		{
			return;
		}
		ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		if (bank == null && !manual)
		{
			return;
		}

		Map<Integer, Long> items = new HashMap<>();
		addItems(items, bank);
		if (config.includeInventory())
		{
			addItems(items, client.getItemContainer(InventoryID.INV));
			addItems(items, client.getItemContainer(InventoryID.WORN));
		}

		JsonArray itemArray = new JsonArray();
		for (Map.Entry<Integer, Long> e : items.entrySet())
		{
			JsonObject o = new JsonObject();
			o.addProperty("id", e.getKey());
			o.addProperty("quantity", e.getValue());
			itemArray.add(o);
		}

		JsonObject levels = new JsonObject();
		for (Skill skill : Skill.values())
		{
			levels.addProperty(skill.getName().toLowerCase(), client.getRealSkillLevel(skill));
		}

		JsonObject body = new JsonObject();
		if (isLinked())
		{
			body.addProperty("token", config.token());
		}
		else
		{
			body.addProperty("code", config.code().trim().toUpperCase());
		}
		body.addProperty("rsn", local.getName());
		body.addProperty("plugin", "ironman-path-sync/" + VERSION);
		body.addProperty("bankKnown", bank != null);
		body.add("levels", levels);
		body.add("items", itemArray);

		final int count = items.size();
		final boolean bankKnown = bank != null;
		final String rsn = local.getName();
		Request request = new Request.Builder()
			.url(ENDPOINT)
			.header("User-Agent", "IronmanPathSync/" + VERSION + " (RuneLite plugin)")
			.post(RequestBody.create(JSON, gson.toJson(body)))
			.build();

		ticksSinceLastSend = 0;
		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Ironman Path sync failed", e);
				report("Could not reach ironmanpath.app (" + e.getMessage() + ").", false);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response r = response)
				{
					String text = r.body() != null ? r.body().string() : "";
					if (r.isSuccessful())
					{
						JsonObject j = null;
						try
						{
							j = gson.fromJson(text, JsonObject.class);
						}
						catch (Exception ex)
						{
							log.debug("bad json", ex);
						}
						if (j != null && j.has("token") && !j.get("token").isJsonNull())
						{
							// Primer envio con codigo: el servidor devuelve la llave permanente.
							configManager.setConfiguration(IronmanPathSyncConfig.GROUP, IronmanPathSyncConfig.KEY_TOKEN, j.get("token").getAsString());
							configManager.setConfiguration(IronmanPathSyncConfig.GROUP, IronmanPathSyncConfig.KEY_RSN, rsn);
							configManager.unsetConfiguration(IronmanPathSyncConfig.GROUP, IronmanPathSyncConfig.KEY_CODE);
							report("Linked as " + rsn + ". Bank synced (" + count + " item types). No more codes needed.", true);
						}
						else
						{
							report("Bank synced (" + count + " item types)" + (bankKnown ? "" : " - bank not opened yet this session, sent inventory + levels only") + ".", true);
						}
					}
					else if (r.code() == 404 && isLinked())
					{
						configManager.unsetConfiguration(IronmanPathSyncConfig.GROUP, IronmanPathSyncConfig.KEY_TOKEN);
						configManager.unsetConfiguration(IronmanPathSyncConfig.GROUP, IronmanPathSyncConfig.KEY_RSN);
						report("This link was removed on the website. Generate a new code to link again.", false);
					}
					else if (r.code() == 404 || r.code() == 410)
					{
						report("Link code invalid, expired or already used. Generate a new one on the website.", false);
					}
					else
					{
						report("Server error " + r.code() + ". Try again in a minute.", false);
					}
				}
				catch (IOException ex)
				{
					report("Bad response from server.", false);
				}
			}
		});
	}

	private void report(String message, boolean ok)
	{
		if (panel != null)
		{
			panel.setResult(message, ok);
			panel.refresh();
		}
		clientThread.invokeLater(() -> chat("Ironman Path: " + message));
	}

	private static void addItems(Map<Integer, Long> items, ItemContainer container)
	{
		if (container == null)
		{
			return;
		}
		for (Item item : container.getItems())
		{
			// id -1 = empty slot. Quantity 0 = bank placeholder (the player does not own it).
			if (item == null || item.getId() <= 0 || item.getQuantity() <= 0)
			{
				continue;
			}
			items.merge(item.getId(), (long) item.getQuantity(), Long::sum);
		}
	}

	private void chat(String message)
	{
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
	}

	/** Icono del panel: casco gris con una flecha naranja (dibujado en codigo, sin archivos). */
	private static BufferedImage icon()
	{
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(150, 150, 150));
		g.fillRoundRect(2, 1, 12, 11, 6, 6);
		g.setColor(new Color(60, 60, 60));
		g.fillRect(4, 7, 8, 2);
		g.setColor(new Color(255, 152, 31));
		g.fillRect(7, 9, 2, 6);
		g.fillPolygon(new int[]{4, 12, 8}, new int[]{12, 12, 16}, 3);
		g.dispose();
		return img;
	}

	@Provides
	IronmanPathSyncConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(IronmanPathSyncConfig.class);
	}
}
