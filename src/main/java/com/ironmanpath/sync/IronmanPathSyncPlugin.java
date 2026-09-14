package com.ironmanpath.sync;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
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
 * to https://www.ironmanpath.app so the site can build the best gear set for a boss
 * from what the player actually owns.
 *
 * Privacy / safety:
 *  - Nothing is sent unless the player typed a valid one-time link code (generated on the site).
 *  - Only game data travels: display name, skill levels and item ids with quantities.
 *  - No password, e-mail, session token or account information is read or sent
 *    (RuneLite does not expose those to plugins anyway).
 *  - Data is read only when the bank is open (the client only knows the bank contents then).
 */
@Slf4j
@PluginDescriptor(
	name = "Ironman Path Sync",
	description = "Send your bank and levels to ironmanpath.app with a one-time code to get the best gear set for a boss",
	tags = {"ironman", "bank", "gear", "dps", "sync"}
)
public class IronmanPathSyncPlugin extends Plugin
{
	static final String ENDPOINT = "https://www.ironmanpath.app/api/sync";
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final int SEND_DELAY_TICKS = 3; // wait ~1.8s after the last bank change before sending
	private static final int MIN_TICKS_BETWEEN_SENDS = 50; // ~30s

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private IronmanPathSyncConfig config;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private Gson gson;

	private int ticksUntilSend = -1;
	private int ticksSinceLastSend = MIN_TICKS_BETWEEN_SENDS;
	private String lastCodeWarned = "";

	@Override
	protected void startUp()
	{
		ticksUntilSend = -1;
		log.debug("Ironman Path Sync started");
	}

	@Override
	protected void shutDown()
	{
		ticksUntilSend = -1;
		log.debug("Ironman Path Sync stopped");
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.BANK)
		{
			return;
		}
		if (!config.autoSend())
		{
			return;
		}
		if (!hasCode())
		{
			warnNoCodeOnce();
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
			// Too soon after the previous send: try again later.
			ticksUntilSend = MIN_TICKS_BETWEEN_SENDS - ticksSinceLastSend;
			return;
		}
		sendSnapshot();
	}

	private boolean hasCode()
	{
		String code = config.code();
		return code != null && code.trim().length() >= 4;
	}

	private void warnNoCodeOnce()
	{
		String code = config.code() == null ? "" : config.code();
		if (lastCodeWarned.equals(code))
		{
			return;
		}
		lastCodeWarned = code;
		chat("Ironman Path: write your link code in the plugin settings to sync your bank (get it at ironmanpath.app > Set builder > My bank).");
	}

	/** Builds the JSON snapshot on the client thread and posts it in the background. */
	private void sendSnapshot()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		Player local = client.getLocalPlayer();
		if (local == null || local.getName() == null)
		{
			return;
		}
		ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		if (bank == null)
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
		body.addProperty("code", config.code().trim().toUpperCase());
		body.addProperty("rsn", local.getName());
		body.addProperty("plugin", "ironman-path-sync/1.0.0");
		body.add("levels", levels);
		body.add("items", itemArray);

		final int count = items.size();
		Request request = new Request.Builder()
			.url(ENDPOINT)
			.header("User-Agent", "IronmanPathSync/1.0.0 (RuneLite plugin)")
			.post(RequestBody.create(JSON, gson.toJson(body)))
			.build();

		ticksSinceLastSend = 0;
		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Ironman Path sync failed", e);
				chatLater("Ironman Path: could not reach ironmanpath.app (" + e.getMessage() + ").");
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response r = response)
				{
					if (r.isSuccessful())
					{
						chatLater("Ironman Path: bank synced (" + count + " item types). Go back to the site.");
					}
					else if (r.code() == 404 || r.code() == 410)
					{
						chatLater("Ironman Path: link code invalid or expired. Generate a new one on the site.");
					}
					else
					{
						chatLater("Ironman Path: server error " + r.code() + ". Try again in a minute.");
					}
				}
			}
		});
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

	private void chatLater(String message)
	{
		clientThread.invokeLater(() -> chat(message));
	}

	@Provides
	IronmanPathSyncConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(IronmanPathSyncConfig.class);
	}
}
