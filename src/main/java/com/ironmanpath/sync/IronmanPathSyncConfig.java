package com.ironmanpath.sync;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(IronmanPathSyncConfig.GROUP)
public interface IronmanPathSyncConfig extends Config
{
	String GROUP = "ironmanpathsync";
	String KEY_CODE = "code";
	String KEY_TOKEN = "token";
	String KEY_RSN = "linkedRsn";

	@ConfigItem(
		keyName = KEY_CODE,
		name = "Link code",
		description = "The 6-letter code shown on ironmanpath.app (Set builder > My bank > Link with RuneLite). Used once; afterwards a private key is stored. You can also use the side panel.",
		position = 1
	)
	default String code()
	{
		return "";
	}

	@ConfigItem(
		keyName = "autoSend",
		name = "Send when I open my bank",
		description = "Send bank, equipment and levels automatically each time the bank is opened.",
		position = 2
	)
	default boolean autoSend()
	{
		return true;
	}

	@ConfigItem(
		keyName = "includeInventory",
		name = "Include inventory and worn items",
		description = "Also send the items in your inventory and the ones you are wearing.",
		position = 3
	)
	default boolean includeInventory()
	{
		return true;
	}

	/* Llave privada que da el servidor al usar el codigo. No se muestra en la configuracion. */
	@ConfigItem(
		keyName = KEY_TOKEN,
		name = "",
		description = "",
		hidden = true
	)
	default String token()
	{
		return "";
	}

	@ConfigItem(
		keyName = KEY_RSN,
		name = "",
		description = "",
		hidden = true
	)
	default String linkedRsn()
	{
		return "";
	}
}
