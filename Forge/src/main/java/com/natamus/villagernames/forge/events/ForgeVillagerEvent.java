package com.natamus.villagernames.forge.events;

import com.natamus.villagernames.cmds.CommandVillagernames;
import com.natamus.villagernames.events.VillagerEvent;
import com.natamus.villagernames.util.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.lang.invoke.MethodHandles;

public class ForgeVillagerEvent {
	// How often (in ticks) to re-check every villager's job against their name. 100 ticks = 5 seconds.
	private static final int PROFESSION_CHECK_INTERVAL_TICKS = 100;
	private static int professionCheckTickCounter = 0;

	public static void registerEventsInBus() {
		BusGroup.DEFAULT.register(MethodHandles.lookup(), ForgeVillagerEvent.class);
	}

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
	public static void onServerTick(TickEvent.ServerTickEvent.Post e) {
		professionCheckTickCounter++;
		if (professionCheckTickCounter < PROFESSION_CHECK_INTERVAL_TICKS) {
			return;
		}
		professionCheckTickCounter = 0;

		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if (server == null) {
			return;
		}

		for (ServerLevel level : server.getAllLevels()) {
			Util.updateVillagerProfessionNames(level);
		}
	}
}
