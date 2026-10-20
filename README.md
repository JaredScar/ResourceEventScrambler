# ResourceEventScrambler

[![forthebadge](https://forthebadge.com/images/badges/built-with-love.svg)](https://badger.store)
[![forthebadge](https://forthebadge.com/images/badges/made-with-java.svg)](https://forthebadge.com)

[![forthebadge](https://forthebadge.com/images/badges/check-it-out.svg)](https://github.com/JaredScar/ResourceEventScrambler/releases)

## What is it?

ResourceEventScrambler is a Java program that was made with ease of usability in mind. It is a tool 
that can be used by FiveM servers to scramble their resources' events. By scrambling the events, this 
in turn makes it tougher for modders to come onto a server injecting pre-made lua menu scripts to 
utilize on your server. Most modders for the most part do not actually know how to code themselves. 
Many modders just use the executor and then insert a mod menu which is the ease of usability for them.
Scrambling your resources will prevent most modders from doing any damage to your server, so it is a 
very smart way of deterring these malicious individuals.

## How to use
All you need to do to use it, is place the .zip you download from 
https://github.com/JaredScar/ResourceEventScrambler/releases (always use the latest release) into your
`fx-server-data` folder (otherwise known as the directory/folder that holds your `server.cfg`). It is 
recommended you back up your `resources` folder before running this program (just to make sure). The 
program will make a backup by default of your `resources` folder, but who knows if it may miss something 
on accident. This is why I recommend backing it up yourself just in case.

1. Unzip the `.zip` into your `fx-server-data` folder (otherwise known as the directory/folder that 
holds your `server.cfg` file)
2. Copy your `resources` folder (the resources folder that has never been scrambled) 
and paste it to make a backup
3. Edit the `config` file to your liking
4. Run the `resourceScramble.bat` to start scrambling your resources
5. Place `RES_Anticheat` in the `resources` folder and start it in your `server.cfg`
6. You are done! Your resources should now be properly scrambled just like eggs, but just no taste...

## RES_Anticheat
After you have ran the scrambler, you will now have a brand new Anticheat to catch people trying to 
mod by triggering the events before you scrambled them. Move the `RES_Anticheat` directory into the 
now scrambled `resources` folder

## How do I add resources?
**After you have scrambled your resources, you can't add new resources to the `resources` directory anymore.**

You will need to add the resources to a backed up `resources` folder, then scramble this folder.
Repeat steps 1-4 again in this case.

## Download
**Source Code (Java):**

https://github.com/JaredScar/ResourceEventScrambler

**Built (with `.bat` executable):**

https://github.com/JaredScar/ResourceEventScrambler/releases



