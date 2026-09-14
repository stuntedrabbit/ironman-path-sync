package com.ironmanpath.sync;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(IronmanPathSyncConfig.GROUP)
public interface IronmanPathSyncConfig extends Config
{
	String GROUP = "ironmanpathsync";

	@ConfigItem(
		keyName = "code",
		name = "Link code",
		description = "The 6-letter code shown on ironmanpath.app (Set builder > My bank > Link with RuneLite). Nothing is sent without a valid code.",
		position = 1
	)
	default String code()
	{
		return "";
	}

	@ConfigItem(
		keyName = "autoSend",
		name = "Send when I open my bank",
		description = "Send bank, equipment and levels automatically each time the bank is opened while the code is valid.",
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
}
