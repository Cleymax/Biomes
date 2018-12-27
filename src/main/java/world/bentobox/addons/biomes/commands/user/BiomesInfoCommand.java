package world.bentobox.addons.biomes.commands.user;


import java.util.*;

import world.bentobox.addons.biomes.commands.ExpandedCompositeCommand;
import world.bentobox.addons.biomes.objects.BiomesObject;
import world.bentobox.bentobox.api.addons.Addon;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.util.Util;


/**
 * This method returns information about provided biome in chat.
 */
public class BiomesInfoCommand extends ExpandedCompositeCommand
{
	public BiomesInfoCommand(Addon addon, BiomesCommand command)
	{
		super(addon, command, "info");
	}

	@Override
	public void setup()
	{
		this.setPermission("island.biomes");
		this.setParametersHelp("biomes.info.parameters");
		this.setDescription("biomes.info.description");

		this.setOnlyPlayer(true);
	}


	@Override
	public boolean execute(User user, String label, List<String> args)
	{
		BiomesObject biomesObject = this.getBiomeObject(args, 0, user);

		if (biomesObject != null)
		{
			user.sendMessage("biomes.info.message.header", "[name]", biomesObject.getFriendlyName());
			user.sendMessage("biomes.info.message.type", "[type]", biomesObject.getBiomeName());
			user.sendMessage("biomes.info.message.description", "[description]", this.getSingleLineDescription(biomesObject.getDescription()));
			user.sendMessage("biomes.info.message.level", "[level]", Long.toString(biomesObject.getRequiredLevel()));
			user.sendMessage("biomes.info.message.cost","[cost]", Integer.toString(biomesObject.getRequiredCost()));

			return true;
		}
		else
		{
			this.showHelp(this, user);
			return false;
		}
	}


	@Override
	public Optional<List<String>> tabComplete(User user, String alias, List<String> args)
	{
 		if (args.size() > 3)
		{
			// Show biome for first tab.
			return Optional.of(new ArrayList<>());
		}

		String lastString = args.get(args.size() - 1);

		final List<String> returnList = new ArrayList<>();

		List<BiomesObject> biomes = this.addon.getAddonManager().getBiomes();

		// Create suggestions with all biomes that is available for users.

		biomes.forEach(biomesObject -> {
			returnList.addAll(Util.tabLimit(
				Collections.singletonList(biomesObject.getBiomeName()), lastString));
		});

		return Optional.of(returnList);
	}
}