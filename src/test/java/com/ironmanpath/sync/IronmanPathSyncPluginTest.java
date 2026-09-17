package com.ironmanpath.sync;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class IronmanPathSyncPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(IronmanPathSyncPlugin.class);
		RuneLite.main(args);
	}
}
