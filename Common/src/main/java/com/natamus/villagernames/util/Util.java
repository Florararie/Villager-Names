package com.natamus.villagernames.util;

import com.natamus.collective.functions.ConfigFunctions;
import com.natamus.collective.functions.EntityFunctions;
import com.natamus.collective.functions.StringFunctions;
import com.natamus.villagernames.config.ConfigHandler;
import com.natamus.villagernames.data.Variables;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Util {
	private static final List<String> namesToOverwrite = new ArrayList<String>(Arrays.asList("Bowman", "Crossbowman", "Horseman", "Nomad", "Recruit", "Shieldman"));
	public static boolean shouldOverwriteName(Entity entity) {
		return namesToOverwrite.contains(entity.getName().getString());
	}

	public static boolean shouldBeNamed(Entity entity) {
		String entityId = EntityType.getKey(entity.getType()).toString();
		if (ConfigFunctions.isInIdList(ConfigHandler.entityTypesToIgnore, entityId)) {
			return false;
		}

		if (ConfigFunctions.isInIdList(ConfigHandler.extraEntityTypesToName, entityId)) {
			return true;
		}

		if (entity instanceof Villager) {
			return true;
		}

		return ConfigHandler.nameModdedVillagers && EntityFunctions.isModdedVillager(entity);
	}

	// Guard Villagers' guards are their own PathfinderMob, not a Villager, so they have to be
	// recognised by entity id. A plain id check keeps this mod building and running without
	// Guard Villagers installed.
	private static final String GUARD_ENTITY_ID = "guardvillagers:guard";
	private static final String GUARD_SUFFIX = "Guard";

	// The separator between a villager's base name and the profession suffix, e.g. "Bob the Mason".
	private static final String SUFFIX_SEPARATOR = " the ";

	// Tag prefix used to remember a villager's base (first) name, separately from
	// whatever profession suffix is currently appended to their display name.
	private static final String BASENAME_TAG_PREFIX = Reference.MOD_ID + ".basename:";

	private static Set<String> knownProfessionSuffixes;

	public static boolean isGuard(Entity entity) {
		Identifier entityId = EntityType.getKey(entity.getType());
		return entityId != null && GUARD_ENTITY_ID.equals(entityId.toString());
	}

	/**
	 * Turns a profession id into the word shown after "the" in a villager's name,
	 * e.g. "Mason", "Leatherworker", "Unemployed", "Nitwit".
	 */
	private static String professionSuffix(String rawProfession) {
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
	 * Returns the profession word to show after "the" in a villager's name.
	 */
	public static String getProfessionSuffix(Villager villager) {
		return professionSuffix(villager.getVillagerData().profession().getRegisteredName());
	}

	/**
	 * Every suffix this mod can bake into a name, so an untracked name can be recognised as one
	 * of ours before anything is rewritten.
	 */
	private static Set<String> getKnownProfessionSuffixes() {
		if (knownProfessionSuffixes == null) {
			Set<String> suffixes = new HashSet<String>();
			suffixes.add("Unemployed");
			suffixes.add("Nitwit");
			suffixes.add(GUARD_SUFFIX);

			for (VillagerProfession profession : BuiltInRegistries.VILLAGER_PROFESSION) {
				Identifier professionId = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
				if (professionId != null) {
					suffixes.add(professionSuffix(professionId.toString()));
				}
			}

			knownProfessionSuffixes = suffixes;
		}

		return knownProfessionSuffixes;
	}

	/**
	 * Strips a trailing " the &lt;profession&gt;" from a name that was generated before the base
	 * name was tracked (an older version of this mod, or a guard that inherited its villager's
	 * name). Returns null when the name doesn't end in a suffix this mod could have written, so
	 * names players chose themselves are left alone.
	 */
	public static String stripProfessionSuffix(String name) {
		int index = name.lastIndexOf(SUFFIX_SEPARATOR);
		if (index <= 0) {
			return null;
		}

		String suffix = name.substring(index + SUFFIX_SEPARATOR.length());
		if (!getKnownProfessionSuffixes().contains(suffix)) {
			return null;
		}

		return name.substring(0, index);
	}

	/**
	 * Builds the full display name for an entity from its base (first) name,
	 * appending " the &lt;Profession&gt;" for Villagers and " the Guard" for Guard Villagers'
	 * guards when the config option is enabled. Everything else just gets the base name.
	 */
	public static String buildDisplayName(Entity entity, String baseName) {
		if (!ConfigHandler.includeProfessionInName) {
			return baseName;
		}

		if (entity instanceof Villager villager) {
			return baseName + SUFFIX_SEPARATOR + getProfessionSuffix(villager);
		}

		if (isGuard(entity)) {
			return baseName + SUFFIX_SEPARATOR + GUARD_SUFFIX;
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
	 * Public accessor for the entity's tracked base (first) name, without any profession suffix.
	 * Returns null if this entity wasn't named by this mod. Used by the trade screen so it
	 * doesn't show the profession twice (once baked into the name, once appended by the screen).
	 */
	public static String getTrackedBaseName(Entity entity) {
		return getBaseName(entity);
	}

	/**
	 * The base name of an entity, recovering it from the display name when it was named by an
	 * older version of this mod that didn't track it. Returns null when the name isn't ours.
	 */
	private static String resolveBaseName(Entity entity) {
		String baseName = getBaseName(entity);
		if (baseName != null) {
			return baseName;
		}

		if (!entity.hasCustomName()) {
			return null;
		}

		String recoveredName = stripProfessionSuffix(entity.getCustomName().getString());
		if (recoveredName == null) {
			return null;
		}

		entity.addTag(BASENAME_TAG_PREFIX + recoveredName);
		return recoveredName;
	}

	private static String getCurrentName(Entity entity) {
		return entity.hasCustomName() ? entity.getCustomName().getString() : null;
	}

	private static void applyName(Entity entity, String baseName) {
		clearBaseName(entity);
		entity.addTag(BASENAME_TAG_PREFIX + baseName);

		EntityFunctions.nameEntity(entity, buildDisplayName(entity, baseName));
	}

	/**
	 * Names an entity with the given base (first) name, remembering that base name
	 * so the profession suffix can later be refreshed independently of it.
	 */
	public static void nameVillager(Entity entity, String baseName) {
		applyName(entity, baseName);
	}

	/**
	 * Gives a guard its "the Guard" name. A guard converted from a named villager inherits the
	 * villager's display name ("Bob the Unemployed") without this mod's tags, so the profession
	 * in that name is replaced with "Guard". Returns true when the name changed.
	 */
	public static boolean nameGuard(Entity guard) {
		if (!ConfigHandler.includeProfessionInName) {
			return false;
		}

		String baseName = resolveBaseName(guard);
		if (baseName == null) {
			if (guard.hasCustomName() || !ConfigHandler.nameModdedVillagers) {
				return false; // Named by something else; don't overwrite it.
			}

			baseName = Names.getRandomName();
			if (baseName.isEmpty()) {
				return false;
			}

			guard.addTag(Reference.MOD_ID + ".named");
		}

		if (buildDisplayName(guard, baseName).equals(getCurrentName(guard))) {
			return false;
		}

		applyName(guard, baseName);
		guard.setCustomNameVisible(true);
		return true;
	}

	/**
	 * Re-checks every tracked villager's profession, and every guard's name, against what is
	 * currently baked into their display name, and renames them when it no longer matches
	 * (job gained, lost or switched, or a villager that was turned into a guard).
	 */
	public static int updateVillagerProfessionNames(ServerLevel serverLevel) {
		if (!ConfigHandler.includeProfessionInName) {
			return 0;
		}

		int updateCount = 0;
		for (Entity entity : serverLevel.getAllEntities()) {
			if (isGuard(entity)) {
				// Only refresh guards that already carry a name. Naming an unnamed guard happens when
				// it loads or through the command, so '/villagernames unname' keeps its effect.
				if (entity.hasCustomName() && nameGuard(entity)) {
					updateCount += 1;
				}
				continue;
			}

			if (!(entity instanceof Villager)) {
				continue;
			}

			String baseName = resolveBaseName(entity);
			if (baseName == null) {
				continue; // Not a villager this mod named.
			}

			String expectedName = buildDisplayName(entity, baseName);
			if (!expectedName.equals(getCurrentName(entity))) {
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
			if (isGuard(entity) || shouldBeNamed(entity)) {
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
			if (isGuard(entity)) {
				if (nameGuard(entity)) {
					nameCount += 1;
				}
				continue;
			}

			if (shouldBeNamed(entity)) {
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
