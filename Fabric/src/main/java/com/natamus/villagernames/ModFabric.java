package com.natamus.villagernames;

import com.natamus.collective.check.RegisterMod;
import com.natamus.collective.check.ShouldLoadCheck;
import com.natamus.villagernames.cmds.CommandVillagernames;
import com.natamus.villagernames.events.VillagerEvent;
import com.natamus.villagernames.util.Reference;
import com.natamus.villagernames.util.Util;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

public class ModFabric implements ModInitializer {

	// How often (in ticks) to re-check every villager's job against their name. 100 ticks = 5 seconds.
	private static final int PROFESSION_CHECK_INTERVAL_TICKS = 100;
	private int professionCheckTickCounter = 0;

	@Override
	public void onInitialize() {
		if (!ShouldLoadCheck.shouldLoad(Reference.MOD_ID)) {
			return;
		}

		setGlobalConstants();
		ModCommon.init();

		loadEvents();

		RegisterMod.register(Reference.NAME, Reference.MOD_ID, Reference.VERSION, Reference.ACCEPTED_VERSIONS);
	}

	private void loadEvents() {
		ServerEntityEvents.ENTITY_LOAD.register((Entity entity, ServerLevel world) -> {
			VillagerEvent.onSpawn(world, entity);
		});

		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			return VillagerEvent.onVillagerInteract(player, world, hand, entity, hitResult);
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			CommandVillagernames.register(dispatcher);
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			professionCheckTickCounter++;
			if (professionCheckTickCounter < PROFESSION_CHECK_INTERVAL_TICKS) {
				return;
			}
			professionCheckTickCounter = 0;

			for (ServerLevel level : server.getAllLevels()) {
				Util.updateVillagerProfessionNames(level);
			}
		});
	}

	private static void setGlobalConstants() {

	}
}
