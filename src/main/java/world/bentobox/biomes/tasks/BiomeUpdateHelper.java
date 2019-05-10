package world.bentobox.biomes.tasks;


import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import java.util.Optional;

import world.bentobox.bentobox.api.addons.Addon;
import world.bentobox.bentobox.api.addons.request.AddonRequestBuilder;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.hooks.VaultHook;
import world.bentobox.biomes.BiomesAddon;
import world.bentobox.biomes.objects.BiomesObject;
import world.bentobox.biomes.objects.Settings.UpdateMode;


/**
 * This class helps to validate if user can change biome. It also calculates how large
 * update must be and calls update task.
 */
public class BiomeUpdateHelper
{
	public BiomeUpdateHelper(Addon addon,
		User callerUser,
		User targetUser,
		BiomesObject biome,
		World world,
		UpdateMode updateMode,
		int updateNumber,
		boolean canWithdraw)
	{
		this.addon = addon;
		this.callerUser = callerUser;
		this.targetUser = targetUser;
		this.biome = biome;
		this.world = world;
		this.updateMode = updateMode;
		this.updateNumber = updateNumber;
		this.canWithdraw = canWithdraw;
	}


	/**
	 * This method returns if update tack can be called.
	 * @return <code>true</code> if user can change biome.
	 */
	public boolean canChangeBiome()
	{
		if (this.callerUser == this.targetUser)
		{
			if (!this.callerUser.hasPermission(this.biome.getPermission()))
			{
				this.callerUser.sendMessage("biomes.messages.errors.missing-permission",
					"[permission]",
					this.biome.getPermission());
				return false;
			}

			if (!this.updateMode.equals(UpdateMode.ISLAND) && this.updateNumber <= 0)
			{
				// Cannot update negative numbers.

				this.callerUser.sendMessage("biomes.messages.errors.incorrect-range",
					TextVariables.NUMBER,
					Integer.toString(this.updateNumber));
				return false;
			}

			Island island = this.addon.getIslands().getIsland(this.world, this.targetUser);

			if (island == null)
			{
				// User has no island.
				this.callerUser.sendMessage("biomes.messages.errors.missing-island");
				return false;
			}

			Optional<Island> onIsland =
				this.addon.getIslands().getIslandAt(this.callerUser.getLocation());

			if (!onIsland.isPresent() || onIsland.get() != island)
			{
				// User is not on his island.

				this.callerUser.sendMessage("biomes.messages.errors.not-on-island");
				return false;
			}

			Optional<VaultHook> vaultHook = this.addon.getPlugin().getVault();

			if (vaultHook.isPresent())
			{
				if (!vaultHook.get().has(this.callerUser, this.biome.getRequiredCost()))
				{
					// Not enough money.

					this.callerUser.sendMessage("biomes.messages.errors.not-enough-money",
						TextVariables.NUMBER,
						Double.toString(this.biome.getRequiredCost()));
					return false;
				}
			}

			Optional<Addon> levelHook = this.addon.getAddonByName("Level");

			if (levelHook.isPresent())
			{
				Object levelObject = new AddonRequestBuilder().addon("Level").
					label("island-level").
					addMetaData("player", this.targetUser.getUniqueId()).
					addMetaData("world-name", this.world.getName()).
					request();

				if (levelObject != null &&
					this.biome.getRequiredLevel() > 0 &&
					(long) levelObject <= this.biome.getRequiredLevel())
				{
					// Not enough level

					this.callerUser.sendMessage("biomes.messages.errors.not-enough-level",
						TextVariables.NUMBER,
						String.valueOf(this.biome.getRequiredLevel()));
					return false;
				}
			}

			// Init starting location.
			this.standingLocation = this.targetUser.getLocation();
		}
		else
		{
			if (this.updateMode.equals(UpdateMode.ISLAND))
			{
				this.standingLocation = this.targetUser.getLocation();

				// Return false if targeted user has no island.
				return this.addon.getIslands().getIsland(this.world, this.targetUser) != null;
			}
			else if (this.callerUser.isPlayer())
			{
				// Chunk and square based update modes can be called only by player.

				Island island = this.addon.getIslands().getIsland(this.world, this.targetUser);

				Optional<Island> onIsland =
					this.addon.getIslands().getIslandAt(this.callerUser.getLocation());

				if (!onIsland.isPresent() || onIsland.get() != island)
				{
					// Admin is not on user island.
					this.callerUser.sendMessage("biomes.messages.errors.missing-admin-island",
						"[user]",
						this.targetUser.getName());

					return false;
				}

				// Admin must be located on island to change biome, as his location will be
				// taken for update.
				this.standingLocation = this.callerUser.getLocation();
			}
			else
			{
				// Check if target user is his island.
				Island island = this.addon.getIslands().getIsland(this.world, this.targetUser);

				Optional<Island> onIsland =
					this.addon.getIslands().getIslandAt(this.targetUser.getLocation());

				if (!onIsland.isPresent() || onIsland.get() != island)
				{
					// Admin is not on user island.
					this.addon.logWarning("Biome change for player " + this.targetUser.getName() + " is not possible as he is not on his island!");
					return false;
				}

				// Init start location
				this.standingLocation = this.targetUser.getLocation();
			}
		}

		return true;
	}


	/**
	 * This method calculates update region and call BiomeUpdateTask to change given biome on island.
	 */
	public void updateIslandBiome()
	{
		Island island = this.addon.getIslands().getIsland(this.world, this.targetUser);
		int range = island.getRange();

		int minX = island.getCenter().getBlockX() - range;
		int minZ = island.getCenter().getBlockZ() - range;

		int maxX = island.getCenter().getBlockX() + range;
		int maxZ = island.getCenter().getBlockZ() + range;

		// Calculate minimal and maximal coordinate based on update mode.

		BiomeUpdateTask task = new BiomeUpdateTask((BiomesAddon) this.addon, this.callerUser, this.world, this.biome);

		switch (this.updateMode)
		{
			case ISLAND:
				task.setMinX(minX);
				task.setMaxX(maxX);
				task.setMinZ(minZ);
				task.setMaxZ(maxZ);

				break;
			case CHUNK:
				Chunk chunk = this.standingLocation.getChunk();

				task.setMinX(Math.max(minX, (chunk.getX() - (this.updateNumber - 1)) << 4));
				task.setMaxX(Math.min(maxX, (chunk.getX() + this.updateNumber) << 4) - 1);

				task.setMinZ(Math.max(minZ, (chunk.getZ() - (this.updateNumber - 1)) << 4));
				task.setMaxZ(Math.min(maxZ, (chunk.getZ() + this.updateNumber) << 4) - 1);

				break;
			case SQUARE:
				int halfDiameter = this.updateNumber / 2;

				int x = this.standingLocation.getBlockX();

				if (x < 0)
				{
					task.setMaxX(Math.max(minX, x + halfDiameter));
					task.setMinX(Math.min(maxX, x - halfDiameter));
				}
				else
				{
					task.setMinX(Math.max(minX, x - halfDiameter));
					task.setMaxX(Math.min(maxX, x + halfDiameter));
				}

				int z = this.standingLocation.getBlockZ();

				if (z < 0)
				{
					task.setMaxZ(Math.max(minZ, z + halfDiameter));
					task.setMinZ(Math.min(maxZ, z - halfDiameter));
				}
				else
				{
					task.setMinZ(Math.max(minZ, z - halfDiameter));
					task.setMaxZ(Math.min(maxZ, z + halfDiameter));
				}

				break;
			default:
				// Setting all values to 0 will skip biome changing.
				// Default should never appear.
				return;
		}

		// Take Money
		if (this.canWithdraw)
		{
			this.addon.getPlugin().getVault().ifPresent(
				vaultHook -> vaultHook.withdraw(this.callerUser, this.biome.getRequiredCost()));
		}

		task.runTaskAsynchronously(this.addon.getPlugin());
	}


// ---------------------------------------------------------------------
// Section: Variables
// ---------------------------------------------------------------------


	/**
	 * This variable stores caller addon.
	 */
	private Addon addon;

	/**
	 * This variable stores User that calls update.
	 */
	private User callerUser;

	/**
	 * This variable stores User that is targeted by update.
	 */
	private User targetUser;

	/**
	 * This variable holds from which location Update process should start.
	 */
	private Location standingLocation;

	/**
	 * This variable stores BiomesObject that must be applied.
	 */
	private BiomesObject biome;

	/**
	 * This variable stores update mode.
	 */
	private UpdateMode updateMode;

	/**
	 * This variable stores how large update region must be.
	 */
	private int updateNumber;

	/**
	 * This variable stores update world.
	 */
	private World world;

	/**
	 * This variable stores if money from caller can be withdrawn.
	 */
	private boolean canWithdraw;
}
