package com.natamus.villagernames.util;

import com.natamus.collective.functions.EntityFunctions;
import com.natamus.collective.functions.StringFunctions;
import com.natamus.villagernames.config.ConfigHandler;
import com.natamus.villagernames.data.Variables;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Util {
	private static final List<String> namesToOverwrite = new ArrayList<String>(Arrays.asList("Bowman", "Crossbowman", "Horseman", "Nomad", "Recruit", "Shieldman"));
	public static boolean shouldOverwriteName(Entity entity) {
		return namesToOverwrite.contains(entity.getName().getString());
	}

	// Tag prefix used to remember a villager's base (first) name, separately from
	// whatever profession suffix is currently appended to their display name.
	private static final String BASENAME_TAG_PREFIX = Reference.MOD_ID + ".basename:";

	/**
	 * Returns the profession word to show after "the" in a villager's name,
	 * e.g. "Mason", "Leatherworker", "Unemployed", "Nitwit".
	 */
	public static String getProfessionSuffix(Villager villager) {
		VillagerData d = villager.getVillagerData();
		String rawProfession = d.profession().getRegisteredName();

		if (rawProfession.contains(":")) {
			rawProfession = rawProfession.split(":")[1];
		}
		if (rawProfession.contains("-")) {
			rawProfession = rawProfession.split("-")[0];
		}

		if (rawProfession.equals("none") || rawProfession.equals("villager")) {
			return "Unemployed";
		}
		if (rawProfession.equals("nitwit")) {
			return "Nitwit";
		}

		String translatableInput = "entity.minecraft.villager." + rawProfession.trim();
		Component profession = Component.translatable(translatableInput);

		if (translatableInput.equals(profession.getString())) {
			rawProfession = rawProfession.replace("_", " ");
			return StringFunctions.capitalizeEveryWord(rawProfession);
		}

		return profession.getString();
	}

	/**
	 * Builds the full display name for an entity from its base (first) name,
	 * appending " the <Profession>" for actual Villagers when the config option
	 * is enabled. Non-villager (e.g. modded villager-likes) just get the base name.
	 */
	public static String buildDisplayName(Entity entity, String baseName) {
		if (!ConfigHandler.includeProfessionInName) {
			return baseName;
		}

		if (entity instanceof Villager villager) {
			String suffix = getProfessionSuffix(villager);
			return baseName + " the " + suffix;
		}

		return baseName;
	}

	private static void clearBaseName(Entity entity) {
		List<String> toRemove = new ArrayList<String>();
		for (String tag : entity.entityTags()) {
			if (tag.startsWith(BASENAME_TAG_PREFIX)) {
				toRemove.add(tag);
			}
		}
		for (String tag : toRemove) {
			entity.removeTag(tag);
		}
	}

	private static String getBaseName(Entity entity) {
		for (String tag : entity.entityTags()) {
			if (tag.startsWith(BASENAME_TAG_PREFIX)) {
				return tag.substring(BASENAME_TAG_PREFIX.length());
			}
		}
		return null;
	}

	/**
	 * Public accessor for the villager's tracked base (first) name, without any
	 * profession suffix. Returns null if this entity wasn't named by this mod.
	 * Used by the trade screen so it doesn't show the profession twice
	 * (once baked into the name, once appended by the trade screen itself).
	 */
	public static String getTrackedBaseName(Entity entity) {
		return getBaseName(entity);
	}

	/**
	 * Names an entity with the given base (first) name, remembering that base name
	 * so the profession suffix can later be refreshed independently of it.
	 */
	public static void nameVillager(Entity entity, String baseName) {
		clearBaseName(entity);
		entity.addTag(BASENAME_TAG_PREFIX + baseName);

		String displayName = buildDisplayName(entity, baseName);
		EntityFunctions.nameEntity(entity, displayName);
	}

	/**
	 * Re-checks every tracked villager's profession against the profession
	 * currently baked into their name, and renames them if it has changed
	 * (job gained, job lost, or job switched).
	 */
	public static int updateVillagerProfessionNames(ServerLevel serverLevel) {
		if (!ConfigHandler.includeProfessionInName) {
			return 0;
		}

		int updateCount = 0;
		for (Entity entity : serverLevel.getAllEntities()) {
			if (!(entity instanceof Villager)) {
				continue;
			}

			String baseName = getBaseName(entity);
			if (baseName == null) {
				continue; // Not a villager this mod named (or named before this feature existed).
			}

			String expectedName = buildDisplayName(entity, baseName);
			String currentName = entity.hasCustomName() ? entity.getCustomName().getString() : null;

			if (!expectedName.equals(currentName)) {
				EntityFunctions.nameEntity(entity, expectedName);
				updateCount += 1;
			}
		}

		return updateCount;
	}

	public static MutableComponent getTradeScreenTitle() {
		Component name = Variables.tradedVillagerPair.getFirst();
		Component profession = Variables.tradedVillagerPair.getSecond();

		if (!ConfigHandler.showProfessionOnTradeScreen) {
			return name.copy();
		}

		if (name.equals(Component.empty()) || profession.equals(Component.empty())) {
			return null;
		}

		MutableComponent newTitle = Component.empty();
		if (!ConfigHandler.switchNameAndProfessionTradeScreen) {
			newTitle = newTitle.append(name).append(" | ").append(profession);
		}
		else {
			newTitle = newTitle.append(profession).append(" | ").append(name);
		}
		return newTitle;
	}

	public static int unnameLoadedVillagers(ServerLevel serverLevel) {
		int unnameCount = 0;

		for (Entity entity : serverLevel.getAllEntities()) {
			if (entity instanceof Villager || EntityFunctions.isModdedVillager(entity)) {
				if (entity.hasCustomName()) {
					entity.setCustomName(null);
					entity.removeTag(Reference.MOD_ID + ".named");
					clearBaseName(entity);

					unnameCount += 1;
				}
			}
		}

		return unnameCount;
	}

	public static int nameLoadedVillagers(ServerLevel serverLevel) {
		int nameCount = 0;

		for (Entity entity : serverLevel.getAllEntities()) {
			if (entity instanceof Villager || EntityFunctions.isModdedVillager(entity)) {
				if (!entity.hasCustomName()) {
					nameVillager(entity, Names.getRandomName());
					entity.addTag(Reference.MOD_ID + ".named");

					nameCount += 1;
				}
			}
		}

		return nameCount;
	}

	public static int renameLoadedVillagers(ServerLevel serverLevel) {
		unnameLoadedVillagers(serverLevel);
		return nameLoadedVillagers(serverLevel);
	}
}
