-------------------------------------------
Source installation information for modders
-------------------------------------------
AE storage and block rules
==========================
With AE2 Unofficial Extended Life (API checked: v0.56.7), carrying a linked
wireless terminal with an enabled magnet card sends chain-mined items to ME
storage. Inventory, offhand and Baubles terminals are supported, including
AE2WUT terminals. Magnet filters, access permissions, range and terminal power
still apply. Unavailable storage and unaccepted items drop at the player's feet;
experience stays at the player's feet.

Hold Shift with the chain key to bypass ME storage for that chain. Both Shift
keys work with the default binding; "Bypass AE storage" can be rebound in the
controls menu. Once bypass is requested, it stays active until that chain ends.
The original rv6-stable-7 AE2 has no magnet card and keeps normal drops.

Use /veinminerplus debug true or /veinminerplus debug false to enable or disable
server diagnostics immediately. The command saves chain_mining.debugLogging in
config/veinminerplus.cfg and works from the server console. Both debug and gui
require permission level 2.

Open /veinminerplus gui, choose "Block rules", then "All-mode blacklist" to edit
the blacklist. Use + to select an exact block with the crosshair and Enter, or
type one block ID, * wildcard, or ore:<OreDictionary name> per row. The blacklist
is empty by default, overrides the whitelist and excludes targets from every
implemented mining and interaction chain mode. The whitelist remains additional
targets for BLAST_ORES. Clients receive the server blacklist on login and after
the administrator saves it; regular manual mining remains available. Install
the same updated VeinMinerPlus build on both the client and server.
GUI saves allow up to 4096 characters per normalized rule list. Longer lists
from the server config still sync in full; shorten them to enable GUI saving.

The client loading screen uses the bundled startup GIF by default. To restore
the Forge logo on the next launch, set B:customStartupAnimation=false in the
client category of config/veinminerplus-client.cfg. This client-only setting
is read before mod initialization and requires a game restart.

This code follows the Minecraft Forge installation methodology. It will apply
some small patches to the vanilla MCP source code, giving you and it access 
to some of the data and functions you need to build a successful mod.

Note also that the patches are built against "unrenamed" MCP source code (aka
srgnames) - this means that you will not be able to read them directly against
normal code.

Source pack installation information:

Standalone source installation
==============================

See the Forge Documentation online for more detailed instructions:
http://mcforge.readthedocs.io/en/latest/gettingstarted/

Step 1: Open your command-line and browse to the folder where you extracted the zip file.

Step 2: You're left with a choice.
If you prefer to use Eclipse:
1. Run the following command: "gradlew genEclipseRuns" (./gradlew genEclipseRuns if you are on Mac/Linux)
2. Open Eclipse, Import > Existing Gradle Project > Select Folder 
   or run "gradlew eclipse" to generate the project.
(Current Issue)
4. Open Project > Run/Debug Settings > Edit runClient and runServer > Environment
5. Edit MOD_CLASSES to show [modid]%%[Path]; 2 times rather then the generated 4.

If you prefer to use IntelliJ:
1. Open IDEA, and import project.
2. Select your build.gradle file and have it import.
3. Run the following command: "gradlew genIntellijRuns" (./gradlew genIntellijRuns if you are on Mac/Linux)
4. Refresh the Gradle Project in IDEA if required.

If at any point you are missing libraries in your IDE, or you've run into problems you can run "gradlew --refresh-dependencies" to refresh the local cache. "gradlew clean" to reset everything {this does not affect your code} and then start the processs again.

Should it still not work, 
Refer to #ForgeGradle on EsperNet for more information about the gradle environment.
or the Forge Project Discord discord.gg/UvedJ9m

Forge source installation
=========================
MinecraftForge ships with this code and installs it as part of the forge
installation process, no further action is required on your part.

LexManos' Install Video
=======================
https://www.youtube.com/watch?v=8VEdtQLuLO0&feature=youtu.be

For more details update more often refer to the Forge Forums:
http://www.minecraftforge.net/forum/index.php/topic,14048.0.html
