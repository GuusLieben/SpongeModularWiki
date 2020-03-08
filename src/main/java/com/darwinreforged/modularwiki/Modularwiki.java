package com.darwinreforged.modularwiki;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.action.TextActions;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.text.format.TextStyles;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

@Plugin(
        id = "modularwiki",
        name = "Modular Wiki",
        description = "Configuration based wiki plugin for Darwin Reforged",
        authors = {
                "DiggyNevs"
        }
)
public class Modularwiki implements CommandExecutor {

    @Inject
    private Logger logger;

    @Inject
    @ConfigDir(sharedRoot = false)
    private Path root;

    public static WikiObject[] wikiObjects;

    private CommandSpec wikiCommandSpec = CommandSpec.builder()
            .permission("modwiki.use")
            .executor(new WikiCommand())
            .arguments(GenericArguments.optional(GenericArguments.string(Text.of("entry"))))
            .build();

    private CommandSpec wikiCommandReload = CommandSpec.builder()
            .permission("modwiki.reload")
            .executor(this)
            .build();

    public static Text defaultBreakLine = getBreakLine("Wiki");

    public static Text getBreakLine(String tag) {
        return Text.of(
                TextColors.DARK_AQUA, TextStyles.STRIKETHROUGH, "============",
                TextStyles.RESET, TextColors.AQUA, String.format(" %s ", tag),
                TextColors.DARK_AQUA, TextStyles.STRIKETHROUGH, "============");
    }

    @Listener
    public void onServerStart(GameStartedServerEvent event) {
        if (this.init()) {
            Sponge.getCommandManager().register(this, wikiCommandSpec, "wiki");
            Sponge.getCommandManager().register(this, wikiCommandReload, "wikireload");
        }
    }

    public boolean init() {
        File configurationFile = new File(this.root.toFile(), "wiki.conf");
        if (!configurationFile.exists()) {
            try {
                configurationFile.getParentFile().mkdirs();
                configurationFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }

        try {
            JsonReader reader = new JsonReader(new FileReader(configurationFile));
            Modularwiki.wikiObjects = new Gson().fromJson(reader, WikiObject[].class);
            return true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            System.out.println("Could not read configuration file, ModularWiki command will not be registered");
            return false;
        }
    }

    @Override
    public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
        src.sendMessage(Text.of(TextColors.GRAY, "[] ", TextColors.AQUA, (this.init() ? "Successfully reloaded wiki" : "Failed to reload wiki, see console for more information")));
        return CommandResult.success();
    }

    public static class WikiCommand implements CommandExecutor {

        @Override
        public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
            Optional<String> optionalEntry = args.getOne("entry");
            if (src instanceof Player) {
                Player pl = (Player) src;

                if (Modularwiki.wikiObjects != null) {
                    if (optionalEntry.isPresent()) {
                        // Specific entry
                        Optional<WikiObject> optionalWikiObject = Arrays.stream(Modularwiki.wikiObjects)
                                .filter(wikiObject -> wikiObject.id.equals(optionalEntry.get()))
                                .findFirst();
                        if (optionalWikiObject.isPresent()) {
                            WikiObject wikiObject = optionalWikiObject.get();
                            if (wikiObject.permission == null || pl.hasPermission(wikiObject.permission)) {
                                Text.Builder multiLineDescriptionBuilder = Text.builder();
                                Arrays.asList(wikiObject.description).forEach(line -> multiLineDescriptionBuilder.append(Text.of("\n", TextColors.WHITE, line)));
                                pl.sendMessage(Text.of(Modularwiki.getBreakLine(wikiObject.name), multiLineDescriptionBuilder.build(), "\n", Modularwiki.getBreakLine(wikiObject.name)));
                            } else {
                                pl.sendMessage(Text.of(TextColors.GRAY, "[] ", TextColors.AQUA, String.format("You do not have permission to view this wiki '%s'", wikiObject.permission)));
                            }
                        } else {
                            pl.sendMessage(Text.of(TextColors.GRAY, "[] ", TextColors.AQUA, String.format("No wiki entries were found for requested value '%s'", optionalEntry.get())));
                        }
                    } else {
                        // List all entries
                        Text.Builder multiLineEntryBuilder = Text.builder();

                        Arrays.asList(Modularwiki.wikiObjects).forEach(wikiObject -> {
                            if (wikiObject.permission == null || pl.hasPermission(wikiObject.permission)) {
                                Text singleEntryText = Text.builder()
                                        .append(Text.of(TextColors.GRAY, "\n - ", TextColors.AQUA, wikiObject.name, TextColors.DARK_AQUA, " [View]"))
                                        .onClick(TextActions.runCommand("/modularwiki:wiki " + wikiObject.id))
                                        .onHover(TextActions.showText(Text.of(TextColors.AQUA, "More information about ", wikiObject.name)))
                                        .build();
                                multiLineEntryBuilder.append(singleEntryText);
                            }
                        });
                        pl.sendMessage(Text.of(Modularwiki.defaultBreakLine, multiLineEntryBuilder.build(), "\n", Modularwiki.defaultBreakLine));
                    }
                } else {
                    pl.sendMessage(Text.of(TextColors.GRAY, "[] ", TextColors.AQUA, "No wiki entries were found"));
                }
            }
            return CommandResult.success();
        }
    }

    public static class WikiObject {

        private String id;
        private String name;
        private String permission;
        private String[] description;

        public WikiObject(String id, String name, String permission, String[] description) {
            this.id = id;
            this.name = name;
            this.permission = permission;
            this.description = description;
        }
    }
}
