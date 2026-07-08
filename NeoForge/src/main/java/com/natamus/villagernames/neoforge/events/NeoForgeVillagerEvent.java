package com.natamus.villagernames.neoforge.events;

import com.natamus.villagernames.cmds.CommandVillagernames;
import com.natamus.villagernames.events.VillagerEvent;
import com.natamus.villagernames.util.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public class NeoForgeVillagerEvent {
	// How often (in ticks) to re-check every villager's job against their name. 100 ticks = 5 seconds.
	private static final int PROFESSION_CHECK_INTERVAL_TICKS = 100;
	private static int professionCheckTickCounter = 0;

	@SubscribeEvent
	public static void onSpawn(EntityJoinLevelEvent e) {
		VillagerEvent.onSpawn(e.getLevel(), e.getEntity());
	}

	@SubscribeEvent
	public static void onPlayerInteract(PlayerInteractEvent.EntityInteractSpecific e) {
		VillagerEvent.onVillagerInteract(e.getEntity(), e.getLevel(), e.getHand(), e.getTarget(), null);
	}

	@SubscribeEvent
	public static void registerCommands(RegisterCommandsEvent e) {
		CommandVillagernames.register(e.getDispatcher());
	}

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post e) {
		professionCheckTickCounter++;
		if (professionCheckTickCounter < PROFESSION_CHECK_INTERVAL_TICKS) {
			return;
		}
		professionCheckTickCounter = 0;

		MinecraftServer server = e.getServer();
		for (ServerLevel level : server.getAllLevels()) {
			Util.updateVillagerProfessionNames(level);
		}
	}
}
