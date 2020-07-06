# TWS-pluginV2

This is the plugin used in **The Worst Server** but as you can see you can use it on your server too so let's get 
started with tutorial.

## Requirements

You need just [Java 8](https://www.youtube.com/watch?v=oY3baOLET5w) to compile the build, 
[git](https://git-scm.com/book/en/v2/Getting-Started-Installing-Git) to keep your build up to date and 
[Mindustry server](https://anuke.itch.io/mindustry) obviously.

## Getting started

When you have git installed you can open command line and use command `git clone https://github.com/jakubDoka/TWS-pluginV2`
now you have to change directory by `cd TWS-pluginV2` and check if things gone well by `git pull`. If message 
`Already up to date.` shows up we can continue, if not join our discord and ask for help or write an issue asking for help.
when you are already in a directory, and you have Java 8 installed use `gradlew jar`. Wait until code compiles. If you get
`BUILD SUCCESSFUL` message then congrats, the hardest part is done.
 
 Go into file system to directory `(Where you cloned the plugin)\The_Worst_v2\build\libs`. There should be a jar file. If
 you installed Midustry server just move the file to `(Where you cloned the server)\mindustry-server\config\mods` and 
 launch the server. Lot of text should show up about default configuration of the plugin. You may already noticed that 
 plugin generated a new directory. More about it is on our [wiki](https://github.com/jakubDoka/TWS-pluginV2/wiki).
