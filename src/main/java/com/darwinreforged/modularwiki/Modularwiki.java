package com.darwinreforged.modularwiki;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.service.pagination.PaginationList;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.action.TextActions;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.text.serializer.TextSerializers;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Plugin(
        id = "modwiki",
        name = "Modular Wiki",
        description = "Configuration based wiki plugin",
        authors = {
                "DiggyNevs"
        },
        version = "1.0.7"
)
public class Modularwiki implements CommandExecutor {

   @Inject
   private Logger logger;

   @Inject
   @ConfigDir (sharedRoot = false)
   private Path root;

   public static WikiObject[] wikiObjects;
   public static WikiConfig wikiConfig;

   private final CommandSpec wikiCommandSpec =
           CommandSpec.builder()
                   .permission("modwiki.use")
                   .executor(new WikiCommand())
                   .arguments(GenericArguments.optional(GenericArguments.string(Text.of("entry"))))
                   .build();

   private final CommandSpec wikiReloadCommandSpec =
           CommandSpec.builder()
                   .permission("modwiki.reload")
                   .executor(this)
                   .build();

   private final CommandSpec wikiShareCommandSpec =
           CommandSpec.builder()
                   .permission("modwiki.share")
                   .executor(new WikiShareCommand())
                   .arguments(GenericArguments.string(Text.of("entry")), GenericArguments.player(Text.of("pl")))
                   .build();

   public static Text getBreakLine ( String tag ) {
      String line = String.join("", Collections.nCopies(12, wikiConfig.padding));
      Text lineText = TextSerializers.FORMATTING_CODE.deserializeUnchecked(line);
      return Text.of(
              lineText,
              TextSerializers.FORMATTING_CODE.deserializeUnchecked(String.format("%s %s ", wikiConfig.getPrimaryColor(), tag)),
              lineText);
   }

   @Listener
   public void onServerStart(GameStartedServerEvent event) {
      if (this.init()) {
         Sponge.getCommandManager().register(this, wikiCommandSpec, "wiki");
         Sponge.getCommandManager().register(this, wikiReloadCommandSpec, "wikireload");
         Sponge.getCommandManager().register(this, wikiShareCommandSpec, "wikishare");
      }
   }

   public boolean init() {
      boolean cf = collectConfig();
      boolean ce = collectEntries();
      return cf && ce;
   }

   private boolean collectConfig() {
      File configFile = new File(this.root.toFile(), "wiki.conf");
      if (!configFile.exists()) {
         try {
            if (!configFile.getParentFile().mkdirs() && !configFile.createNewFile())
               throw new IOException("Failed to create configuration file, do I have permission?");

            WikiConfig config = new WikiConfig("&3=", "&bWiki", "&7[] &b", "b", "3");
            String configStr = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(config);
            FileWriter writer = new FileWriter(configFile);
            writer.write(configStr);
            writer.close();
         } catch (IOException e) {
            this.logger.error(e.getMessage());
            return false;
         }
      }

      try (JsonReader reader = new JsonReader(new FileReader(configFile))) {
         Modularwiki.wikiConfig = new Gson().fromJson(reader, WikiConfig.class);
         return true;
      } catch (IOException e) {
         this.logger.error(e.getMessage());
         this.logger.warn("Could not read configuration file, ModularWiki command will not be registered");
         return false;
      }
   }

   private boolean collectEntries() {
      File entryFile = new File(this.root.toFile(), "entries.conf");
      if (!entryFile.exists()) {
         try {
            if (!entryFile.getParentFile().mkdirs() && !entryFile.createNewFile())
               throw new IOException("Failed to create entry file, do I have permission?");

            WikiObject[] wikiObjects = new WikiObject[] {
                    new WikiObject(
                            "some_wiki",
                            "Wiki Sample",
                            "wiki.admin",
                            new String[] {
                                    "another_wiki|Some description line with click actions to open another wiki. The click action is set by starting the line with the ID of another wiki, followed by a vertical line",
                                    "Another line without click actions",
                                    "This wiki is only visible to players with the wiki.admin permission"
                            }, false, true),
                    new WikiObject(
                            "another_wiki",
                            "Another Sample",
                            null,
                            new String[] {
                                    "This wiki will not appear in the list",
                                    "But it will open using the click action on the first line of 'Wiki Sample'",
                                    "Also, you cannot share this wiki, as it is disabled with the 'share' setting"
                            }, true, false
                    )
            };

            logger.info("Starting serialization of entries");
            String wikiObjectsStr = new GsonBuilder().setPrettyPrinting().create().toJson(wikiObjects);
            FileWriter writer = new FileWriter(entryFile);
            writer.write(wikiObjectsStr);
            writer.close();
            logger.info("Ended serialization of entries");
         } catch (IOException e) {
            this.logger.error(e.getMessage());
            return false;
         }
      }

      try (JsonReader reader = new JsonReader(new FileReader(entryFile))) {
         Modularwiki.wikiObjects = new Gson().fromJson(reader, WikiObject[].class);
         return true;
      } catch (IOException e) {
         this.logger.error(e.getMessage());
         this.logger.warn("Could not read entry file, ModularWiki command will not be registered");
         return false;
      }
   }

   public static Text getPrefix() {
      return parse(wikiConfig.prefix);
   }

   private static void tellPlayer(String message, CommandSource pl) {
      pl.sendMessage(Text.of(getPrefix(), message));
   }

   @Override
   public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
      Modularwiki.tellPlayer((this.init() ? "Successfully reloaded wiki" : "Failed to reload wiki, see console for more information"), src);
      return CommandResult.success();
   }

   public static class WikiCommand implements CommandExecutor {

      @Override
      public CommandResult execute ( CommandSource src, CommandContext args ) throws CommandException {
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
                        Arrays.asList(wikiObject.description).forEach(line -> {
                           String[] partialLines = line.split("\\|");
                           if (partialLines.length == 2) {
                              Text.Builder singleLineBuilder = Text.builder();
                              singleLineBuilder
                                      .append(Text.of("\n", TextColors.WHITE, partialLines[1]))
                                      .onHover(TextActions.showText(parse(String.format(wikiConfig.getPrimaryColor() + "Open entry '%s'", partialLines[0]))))
                                      .onClick(TextActions.runCommand(String.format("/modwiki:wiki %s", partialLines[0])));

                              multiLineDescriptionBuilder.append(singleLineBuilder.build());
                           } else multiLineDescriptionBuilder.append(Text.of("\n", TextColors.WHITE, line));
                        });

                        Text shareButton = Text.EMPTY;
                        if (wikiObject.share) {
                           shareButton = Text.builder()
                                   .append(Text.of("\n", parse(wikiConfig.getSecondaryColor() + "["), parse(wikiConfig.getPrimaryColor() + String.format("Share '%s'", wikiObject.name)), parse(wikiConfig.getSecondaryColor() + "]"), Text.NEW_LINE))
                                   .onHover(TextActions.showText(Text.of(parse(wikiConfig.getPrimaryColor()), "Share wiki with another player")))
                                   .onClick(TextActions.suggestCommand(String.format("/modwiki:wikishare %s", wikiObject.id)))
                                   .build();
                        }
                        pl.sendMessage(Text.of(Modularwiki.getBreakLine(wikiObject.name), multiLineDescriptionBuilder.build(), "\n", shareButton, Modularwiki.getBreakLine(wikiObject.name)));
                     } else {
                        Modularwiki.tellPlayer(String.format("You do not have permission to view this wiki '%s'", wikiObject.permission), pl);
                     }
                  } else {
                     Modularwiki.tellPlayer(String.format("No wiki entries were found for requested value '%s'", optionalEntry.get()), pl);
                  }

               } else {
                  // List all entries
                  List<Text> entriesText = new ArrayList<>();
                  Arrays.asList(Modularwiki.wikiObjects).forEach(wikiObject -> {
                     if (wikiObject.permission == null || pl.hasPermission(wikiObject.permission) && !wikiObject.hide) {
                        Text singleEntryText = Text.builder()
                                .append(Text.of(parse(" &7- "), parse(wikiConfig.getPrimaryColor() + wikiObject.name), parse(wikiConfig.getSecondaryColor() + " [View]")))
                                .onClick(TextActions.runCommand("/modwiki:wiki " + wikiObject.id))
                                                       .onHover(TextActions.showText(Text.of(parse(wikiConfig.getPrimaryColor()), "More information about ", wikiObject.name)))
                                                       .build();
                        entriesText.add(singleEntryText);
                     }
                  });
                  PaginationList.builder().contents(entriesText).title(parse(wikiConfig.default_title)).padding(parse(wikiConfig.padding)).linesPerPage(10).sendTo(pl);
               }
            } else {
               Modularwiki.tellPlayer("No wiki entries were found", pl);
            }
         }
         return CommandResult.success();
      }
   }

   public static Text parse(String in) {
      return TextSerializers.FORMATTING_CODE.deserializeUnchecked(in);
   }

   public static class WikiObject {

      private final String id;
      private final String name;
      private final String permission;
      private final String[] description;
      private final boolean hide;
      private final boolean share;

      public WikiObject(String id, String name, String permission, String[] description, boolean hide, boolean share) {
         this.id = id;
         this.name = name;
         this.permission = permission;
         this.description = description;
         this.hide = hide;
         this.share = share;
      }

      public WikiObject(String id, String name, String permission, String[] description, boolean hide) {
         this.id = id;
         this.name = name;
         this.permission = permission;
         this.description = description;
         this.hide = hide;
         this.share = true;
      }
   }

   public static class WikiConfig {

      private final String padding;
      private final String default_title;
      private final String prefix;
      private final String primary_color;
      private final String secondary_color;

      public WikiConfig(String padding, String default_title, String prefix, String primary_color, String secondary_color) {
         this.padding = padding;
         this.default_title = default_title;
         this.prefix = prefix;
         this.primary_color = primary_color;
         this.secondary_color = secondary_color;
      }

      public String getPrimaryColor() {
         return primary_color.startsWith("&") ? primary_color : "&" + primary_color;
      }

      public String getSecondaryColor() {
         return secondary_color.startsWith("&") ? secondary_color : "&" + secondary_color;
      }
   }

   public static class WikiShareCommand implements CommandExecutor {

      @Override
      public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
         String entry = (String) args.getOne("entry").orElse(null);
         Player player = (Player) args.getOne("pl").orElse(null);
         if (entry != null && player != null) {
            WikiObject wikiObject = Arrays.stream(Modularwiki.wikiObjects).filter(wikiObj -> wikiObj.id.equals(entry)).findFirst().orElse(null);
            if (wikiObject != null) {
               Text shareMessage = Text.of(TextColors.GRAY, "[] ", parse(wikiConfig.getSecondaryColor()), src.getName(), parse(wikiConfig.getPrimaryColor()), String.format(" shared the '%s' wiki with you ", wikiObject.name));
               Text viewButton = Text.builder()
                       .append(Text.of(parse(wikiConfig.getSecondaryColor()), "[", parse(wikiConfig.getPrimaryColor()), "View", parse(wikiConfig.getSecondaryColor()), "]"))
                       .onHover(TextActions.showText(Text.of(parse(wikiConfig.getPrimaryColor()), String.format("View entry '%s'", entry))))
                       .onClick(TextActions.runCommand(String.format("/modwiki:wiki %s", entry))).build();

               player.sendMessage(Text.of(shareMessage, viewButton));
            } else
               Modularwiki.tellPlayer(String.format("No wiki entries were found for requested value '%s'", entry), src);
         } else Modularwiki.tellPlayer("Could not share entry", src);
         return CommandResult.success();
      }

   }
}
