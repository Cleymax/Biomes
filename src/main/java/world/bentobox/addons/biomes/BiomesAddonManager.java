package world.bentobox.addons.biomes;


import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import world.bentobox.addons.biomes.objects.BiomesObject;
import world.bentobox.addons.biomes.panel.BiomesPanel;
import world.bentobox.addons.biomes.utils.Utils;
import world.bentobox.bentobox.api.configuration.Config;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.util.Util;


/**
 * This class contains all necessary thinks for Biomes Addon. It tries to manage and hold all necessary
 * information.
 */
public class BiomesAddonManager
{
	/**
	 * This is default constructor for Addon Manager.
	 * @param addon Inits addon manager.
	 */
	protected BiomesAddonManager(BiomesAddon addon)
	{
		this.addon = addon;

		this.biomesConfig = new Config<>(addon, BiomesObject.class);
		this.biomesList = new ArrayList<>(Biome.values().length);

		this.biomesFile = new File(this.addon.getDataFolder(), "biomes.yml");

		if (!this.biomesFile.exists())
		{
			this.addon.saveResource("biomes.yml", false);
		}

		this.initBiomes();
	}


// ---------------------------------------------------------------------
// Section: Loading
// ---------------------------------------------------------------------


	/**
	 * Creates biomes cache.
	 */
	private void initBiomes()
	{

		this.biomesList.clear();
		this.addon.getLogger().info("Loading biomes...");

		this.biomesConfig.loadConfigObjects().forEach(this::storeBiome);

		this.biomesList.sort(Comparator.comparingInt(BiomesObject::getBiomeID));
	}


// ---------------------------------------------------------------------
// Section: Storing
// ---------------------------------------------------------------------


	/**
	 * Save configs and biomes data
	 */
	private void save()
	{
		this.biomesList.forEach(this.biomesConfig::saveConfigObject);
	}


	/**
	 * Save to the database
	 * @param async - if true, saving will be done async
	 */
	public void save(boolean async)
	{
		if (async)
		{
			this.addon.getServer().getScheduler().
				runTaskAsynchronously(this.addon.getPlugin(), this::save);
		}
		else
		{
			this.save();
		}
	}


	/**
	 * Store biomes silently. Used when loading.
	 * @param biome that must be stored.
	 * @return true if successful
	 */
	private boolean storeBiome(BiomesObject biome)
	{
		return this.storeBiome(biome, true, null, true);
	}


	/**
	 * Stores the biomes.
	 * @param biome - biome that must be stored.
	 * @param overwrite - true if previous biomes should be overwritten
	 * @param user - user making the request
	 * @param silent - if true, no messages are sent to user
	 * @return - true if imported
	 */
	public boolean storeBiome(BiomesObject biome, boolean overwrite, User user, boolean silent)
	{
		// Contains in array list is not fast.. but list is not so large, so it is ok there.

		if (this.biomesList.contains(biome))
		{
			if (!overwrite)
			{
				if (!silent)
				{
					user.sendMessage("biomes.admin.import.skipping",
						"[biomes]",
						biome.getFriendlyName());
				}

				return false;
			}
			else
			{
				if (!silent)
				{
					user.sendMessage("biomes.admin.import.overwriting",
						"[biomes]",
						biome.getFriendlyName());
				}

				this.biomesList.set(this.biomesList.indexOf(biome), biome);

				return true;
			}
		}

		if (!silent)
		{
			user.sendMessage("biomes.admin.import.imported",
				"[biomes]",
				biome.getFriendlyName());
		}

		this.biomesList.add(biome);

		return true;
	}


// ---------------------------------------------------------------------
// Section: Importing
// ---------------------------------------------------------------------


	/**
	 * This method imports biomes
	 *
	 * @param user - user
	 * @param world - world to import into
	 * @param overwrite - true if previous ones should be overwritten
	 * @return true if successful
	 */
	public boolean importBiomes(User user, World world, boolean overwrite)
	{
		if (!this.biomesFile.exists())
		{
			user.sendMessage("biomes.admin.import.no-file");
			return false;
		}

		YamlConfiguration config = new YamlConfiguration();

		try
		{
			config.load(this.biomesFile);
		}
		catch (IOException | InvalidConfigurationException e)
		{
			user.sendMessage("biomes.admin.import.no-load",
				"[message]",
				e.getMessage());
			return false;
		}

		this.readBiomes(config, user, world, overwrite);
		this.biomesList.sort(Comparator.comparingInt(BiomesObject::getBiomeID));
		this.addon.getAddonManager().save(false);
		return true;
	}


	/**
	 * This method creates biomes object from config file.
	 * @param config YamlConfiguration that contains all biomes.
	 * @param user User who calls reading.
	 * @param world World in which method is called.
	 * @param overwrite Boolean that indicate if biomes should be overwritted.
	 */
	private void readBiomes(YamlConfiguration config, User user, World world, boolean overwrite)
	{
		int size = 0;

		ConfigurationSection reader = config.getConfigurationSection("biomes.biomesList");

		Map<String, Biome> biomeNameMap = Utils.getBiomeNameMap();

		for (String biome : reader.getKeys(false))
		{
			if (biomeNameMap.containsKey(biome.toUpperCase()))
			{
				BiomesObject newBiomeObject = new BiomesObject(
					biomeNameMap.get(biome.toUpperCase()));

				newBiomeObject.setUniqueId(Util.getWorld(world).getName() + "_" + biome);
				newBiomeObject.setDeployed(true);

				ConfigurationSection details = reader.getConfigurationSection(biome);

				newBiomeObject.setFriendlyName(details.getString("friendlyName", biome));

				newBiomeObject.setDescription(
					Utils.splitString(details.getString("description", "")));
				newBiomeObject.setIcon(
					Utils.parseItem(this.addon, details.getString("icon") + ":1"));

				newBiomeObject.setRequiredLevel(details.getInt("islandLevel", 0));
				newBiomeObject.setRequiredCost(details.getInt("cost", 0));

				if (this.addon.getAddonManager().storeBiome(
					newBiomeObject, overwrite, user, false))
				{
					size++;
				}
			}
			else
			{
				user.sendMessage("biomes.admin.import.wrong",
					"[biome]",
					biome);
			}
		}

		user.sendMessage("biomes.admin.import.number",
			"[number]",
			String.valueOf(size));
	}


// ---------------------------------------------------------------------
// Section: Getters / Setters
// ---------------------------------------------------------------------


	/**
	 * This method returns list with loaded biomes.
	 * @return list with loaded biomes.
	 */
	public List<BiomesObject> getBiomes()
	{
		return this.biomesList;
	}


// ---------------------------------------------------------------------
// Section: Variables
// ---------------------------------------------------------------------

	/**
	 * Variable current addon.
	 */
	private BiomesAddon addon;

	/**
	 * Variable stores Biomes Panel.
	 */
	private BiomesPanel biomesPanel;

	/**
	 * Variable stores list of loaded biomes.
	 */
	private List<BiomesObject> biomesList;

	/**
	 * Variable stores biomes object configuration.
	 */
	private Config<BiomesObject> biomesConfig;

	/**
	 * Variable stores biomes.yml location
	 */
	private File biomesFile;
}
