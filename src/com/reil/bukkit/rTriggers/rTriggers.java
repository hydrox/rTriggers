package com.reil.bukkit.rTriggers;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.logging.*;

import org.bukkit.entity.*;
import org.bukkit.Server;
import org.bukkit.event.Event;
import org.bukkit.event.Event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.server.*;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.*;
import org.bukkit.command.*;

import org.bukkit.croemmich.serverevents.*;
import com.ensifera.animosity.craftirc.CraftIRC;
import com.iConomy.iConomy;
import com.nijiko.permissions.PermissionHandler;
import com.nijikokun.bukkit.Permissions.Permissions;
import com.reil.bukkit.rParser.rParser;

@SuppressWarnings("unused")
public class rTriggers extends JavaPlugin {
	private ConsoleCommandSender Console;
	private boolean registered = false;
	public rPropertiesFile Messages;
	public Server MCServer;
	public Random RNG;
	public Logger log;
	
	rTriggersServerListener serverListener = new rTriggersServerListener(this);
	PlayerListener playerListener = new rTriggersPlayerListener(this);
	EntityListener entityListener = new rTriggersEntityListener(this);
	
	public iConomy iConomyPlugin;
	public CraftIRC CraftIRCPlugin;
	public PermissionHandler PermissionsPlugin;
	public Plugin ServerEventsPlugin;
    
    HashMap <String, Integer> listTracker = new HashMap<String,Integer>();
	HashMap <Integer, EntityDamageEvent.DamageCause> deathCause = new HashMap <Integer, EntityDamageEvent.DamageCause>();
	HashMap <Integer, Entity> deathBringer = new HashMap <Integer, Entity>();

    /**
     * Goes through each message in messages[] and registers events that it sees in each.
     * @param messages
     */
	public void registerEvents(String[] messages){
		if (registered) return;
		else registered = true;
		PluginManager loader = MCServer.getPluginManager();
		boolean [] flag = new boolean[7];
		Arrays.fill(flag, false);
		for(String message : messages){
			String [] split = message.split(":");
			if(split.length >= 2){
				String options = split[1];
				if(!flag[0] && (options.contains("onlogin") || options.isEmpty())){
					loader.registerEvent(Event.Type.PLAYER_JOIN, playerListener, Priority.Monitor, this);
					flag[0] = true;
				}
				if(!flag[1] && options.contains("ondisconnect")){
					loader.registerEvent(Event.Type.PLAYER_QUIT, playerListener, Priority.Monitor, this);
					flag[1] = true;
				}
				if(!flag[2] && options.contains("oncommand")){
					loader.registerEvent(Event.Type.PLAYER_COMMAND_PREPROCESS, playerListener, Priority.Monitor, this);
					flag[2] = true;
				}
				if(!flag[3] && options.contains("onkick")){
					loader.registerEvent(Event.Type.PLAYER_KICK, playerListener, Priority.Monitor, this);
					flag[3] = true;
				}
				if(!flag[4] && options.contains("ondeath")){
					loader.registerEvent(Event.Type.ENTITY_DAMAGE, entityListener, Priority.Monitor, this);
					loader.registerEvent(Event.Type.ENTITY_DEATH, entityListener, Priority.Monitor, this);
					flag[4] = true;
				}
				if(!flag[5] && options.contains("onconsole")){
					loader.registerEvent(Event.Type.SERVER_COMMAND, serverListener, Priority.Monitor, this);
					flag[5] = true;
				}
				if(!flag[6] && options.contains("onload")){
					for (String option: options.split(",")){
						if (option.startsWith("onload|")) {
							String pluginName = option.substring("onload|".length());
							serverListener.listenFor(pluginName);
						}
					}
				}
			}
		}
		
		loader.registerEvent(Event.Type.PLUGIN_ENABLE, serverListener, Priority.Monitor, this);
		loader.registerEvent(Event.Type.PLUGIN_DISABLE, serverListener, Priority.Monitor, this);
	} 
	public void onEnable(){
		log = Logger.getLogger("Minecraft");
		RNG = new Random();
		MCServer = getServer();
		Console = new ConsoleCommandSender(MCServer);
		getDataFolder().mkdir();
        Messages = new rPropertiesFile(getDataFolder().getPath() + "/rTriggers.properties");

		try {
			grabPlugins();
			registerEvents(Messages.load());
		} catch (Exception e) {
			log.log(Level.SEVERE, "[rTriggers]: Exception while loading properties file.", e);
		}
		generateTimers();
		
		// Do onload events for everything that might have loaded before rTriggers
		serverListener.checkAlreadyLoaded();
		
		log.info("[rTriggers] Loaded: Version " + getDescription().getVersion());
	}
	
	/**
	 *  Checks to see if plugins which rTriggers supports have already been loaded.
	 *  Registers rTriggers with already-loaded plugins it finds.
	 */
	public void grabPlugins() {
		if (PermissionsPlugin == null && MCServer.getPluginManager().getPlugin("Permissions") != null){
        	PermissionsPlugin = Permissions.Security;
        	log.info("[rTriggers] Attached to Permissions.");
        }
        
        Plugin iConomyTry = MCServer.getPluginManager().getPlugin("iConomy");
        if (iConomyPlugin == null && iConomyTry != null){
        	iConomyPlugin = (iConomy) iConomyTry;
        	log.info("[rTriggers] Attached to iConomy.");
        }
        
        Plugin CraftIRCTry = this.getServer().getPluginManager().getPlugin("CraftIRC");
        if (CraftIRCPlugin == null && CraftIRCTry != null){
        	CraftIRCPlugin = (CraftIRC) CraftIRCTry;
        	log.info("[rTriggers] Attached to CraftIRC.");
        }
	}
	
	/*
	 * Precondition: We already have messages loaded
	 * Postcondition: New threads for each timer have been created.  
	 */
	public void generateTimers(){
		for(String key : Messages.getKeys()){
			try {
				if (key.startsWith("<<timer|")){
					for(String message : Messages.getStrings(key)){
						MCServer.getScheduler().scheduleAsyncRepeatingTask (this,
								new rTriggersTimer(this, message),
								0,
								20 * new Long(key.substring(8, key.length()-2)));
					}
				}
			} catch (NumberFormatException e){
				log.log(Level.WARNING, "[rTriggers] Invalid number string:" + key);
			}
		}
		if (Messages.keyExists("<<timer>>")) log.log(Level.WARNING, "[rTriggers] Using old timer format! Please update to new version.");
	}
	
	@Override
	public void onDisable(){
		Messages.save();
		MCServer.getScheduler().cancelTasks(this);
		PluginManager loader = MCServer.getPluginManager();
		log.info("[rTriggers] Disabled!");
	} 
	
	
	/* Looks through all of the messages,
	 * Sends the messages triggered by groups which 'triggerMessage' is a member of,
	 * But only if that message has the contents of 'option' as one of its options */
	public boolean triggerMessagesWithOption(Player triggerMessage, String option){
		String[] eventToReplace = new String[0];
		String[] eventReplaceWith = new String[0];
		return triggerMessagesWithOption(triggerMessage, option, eventToReplace, eventReplaceWith);
	}
	public boolean triggerMessagesWithOption(String option, String[] eventToReplace, String []eventReplaceWith){
		return triggerMessagesWithOption(null, option, eventToReplace, eventReplaceWith);
	}
	
	public boolean triggerMessagesWithOption(Player triggerMessage, String option, String[] eventToReplace, String[] eventReplaceWith){
		ArrayList<String>groupArray = new ArrayList<String>();
		boolean triggeredMessage = false;
		if (triggerMessage != null){
			/* Everyone triggers their own name */
			groupArray.add("<<player|" + triggerMessage.getName() + ">>");
			/* Everyone triggers, well, <<everyone>> */
			groupArray.add("<<everyone>>");
			/* Add any groups the user's a member of. */
			if(PermissionsPlugin != null) groupArray.addAll(Arrays.asList(PermissionsPlugin.getGroups(triggerMessage.getWorld().getName(),triggerMessage.getName())));
		} else {
			// If there's no player, then we have a custom trigger field
			groupArray.add("<<customtrigger>>");
		}
		
		/* Check for messages triggered by each group the player is a member of. */
		for (String groupName : groupArray){
			if (!Messages.keyExists(groupName)) continue;
			if (groupName.startsWith("<<") && groupName.startsWith("<<list|")) groupName = groupName.toLowerCase();
			// Check all the messages for this group 
			for (String sendToGroups_Message : Messages.getStrings(groupName)){
				boolean hookValid = false;
				String [] split =  sendToGroups_Message.split(":");
				String [] options =  split[1].split(",");
				
				// See if any of the options of this message match the one we called the funciton with
				if (split[1].isEmpty() && option.equalsIgnoreCase("onlogin")){
					// Default case: No options is equivalent to onlogin
					hookValid = true;
				} else for (int i = 0; i < options.length && hookValid == false; i++){
					// Otherwise, just check each option, see if it matches the parameter
					hookValid = options[i].equalsIgnoreCase(option);
				}
				
				if (!hookValid) continue;
				
				// If the message matched our option, we sort it out and send it	
				/**************************
				 * Tag replacement start!
				 *************************/
				
				String message = rParser.combineSplit(2, split, ":");
				
				message = replaceLists(message);
				
				// Regex's which catch @, but not \@ and &, but not \&
				String [] replace = {"(?<!\\\\)@", "(?<!\\\\)&", "<<color>>","<<placeholder>>"};
				String [] with    = {"\n�f"      , "�"         , "�"        ,""};
				message = rParser.replaceWords(message, replace, with);
				
				String [] replace2 = { "<<triggerer>>", "<<triggerer-ip>>", "<<triggerer-locale>>", "<<triggerer-country>>", "<<triggerer-balance>>" };
				String [] with2    = getTagReplacements(triggerMessage);
				message = rParser.replaceWords(message, replace2, with2);
				
				
				if (eventToReplace.length > 0)
					message = rParser.replaceWords(message, eventToReplace, eventReplaceWith);
				/**************************
				 *  Tag replacement end! */
				
				sendMessage(message, triggerMessage, split[0]);
				triggeredMessage = true;
			}
		}
		return triggeredMessage;
	}
	
	/*
	 * Will replace user-generated lists, as well as the player list.
	 */
	public String replaceLists(String message) {
		int optionStart;
		int optionEnd;
		String listMember;
		
		// Replace user-generated lists:
		while ( (optionStart = message.indexOf("<<list|")) != -1){
			optionStart += "<<list|".length();
			optionEnd = message.indexOf(">>", optionStart);
			String options = message.substring(optionStart, optionEnd);
			String [] optionSplit = options.split("\\|");
			// Call up the list
			String [] messageList = Messages.getStrings("<<list|" + optionSplit[0] + ">>");
			if (messageList.length > 0){
				if (optionSplit.length > 1 && optionSplit[1].equalsIgnoreCase("rand")){
						listMember = messageList[RNG.nextInt(messageList.length)];
				} else {
					if(!listTracker.containsKey(optionSplit[0]))
						listTracker.put(optionSplit[0], 0);
					int listNumber = listTracker.get(optionSplit[0]);
					listMember = messageList[listNumber];
					listTracker.put(optionSplit[0], (listNumber + 1)%messageList.length);
				}
			} else listMember = "";
			message = message.replace("<<list|" + options + ">>", listMember);
		}
		
		// Now replace any use of <<player-list>>
		if(message.contains("<<player-list>>")){
			StringBuilder list = new StringBuilder();
			String prefix = "";
			
			for (Player getName : MCServer.getOnlinePlayers()){
				list.append(prefix + getName.getDisplayName());
				prefix = ", ";
			}
			message = message.replaceAll("<<player-list>>", list.toString());
		}
		
		return message;
	}
	/**
	 * Use in conjunction with rParser.replaceWords or rParser.parseMessage;
	 * @param player A player to get the replacements for
	 * @return Array of things to replace tags in this order:
	 *         Name, IP address, locale, country, iConomy balance
	 */
	public String[] getTagReplacements(Player player){
		if (player == null){
			String [] returnArray = {"", "", "", "", ""};
			return returnArray;
		}
		// Get balance tag
		double balance = 0;
		if (iConomyPlugin != null && iConomy.hasAccount(player.getName()))
			balance = iConomy.getAccount(player.getName()).getHoldings().balance();
		
		// Get ip and locale tags
		InetSocketAddress triggerIP = player.getAddress();
		String triggerCountry;
		String triggerLocale;
		try {
			Locale playersHere = net.sf.javainetlocator.InetAddressLocator.getLocale(triggerIP.getAddress());
			triggerCountry = playersHere.getDisplayCountry();
			triggerLocale = playersHere.getDisplayName();
		} catch (Exception e){
			triggerCountry = ""; 
			triggerLocale = "";
		}
		String [] returnArray = { player.getName(), triggerIP.toString(), triggerLocale, triggerCountry, Double.toString(balance)};
		return returnArray; 
	}

	public void sendMessage(String message, Player triggerMessage, String Groups){
		/* Default: Send to player unless other groups are specified.
		 * If so, send to those instead. */
		if (Groups.isEmpty() || Groups.equalsIgnoreCase("<<triggerer>>"))
			sendToPlayer(message, triggerMessage, false, false);
		else
			sendToGroups(Groups.split(","), message, triggerMessage);
	}

	/**
	 * Takes care of 'psuedo-groups' like <<triggerer>>, <<server>>, and <<everyone>>,
	 * then sends to the rest of the normal groups.
	 * @param sendToGroups An array of groups and pseudo-groups to send this message to
	 * @param message The message you want to send
	 * @param triggerer The player that triggered this message (can be null, if no triggerer)
	 */
	public void sendToGroups (String [] sendToGroups, String message, Player triggerer) {
		String [] replace = {"<<recipient>>", "<<recipient-ip>>", "<<recipient-color>>", "<<recipient-balance>>", "�"};
		ArrayList <String> sendToGroupsFiltered = new ArrayList<String>();
		HashSet <Player> sendToUs = new HashSet<Player>();
		boolean flagCommand  = false;
		boolean flagSay      = false;
		/*************************************
		 * Begin:
		 * 1) Constructing list of groups to send to
		 * 2) Processing 'special' groups (ones in double-chevrons) */
		for (String group : sendToGroups){
			if (!group.startsWith("<<")) sendToGroupsFiltered.add(group);
			/* Special cases: start! */
			else if (group.equalsIgnoreCase("<<everyone>>"))          for (Player addMe : MCServer.getOnlinePlayers()) sendToUs.add(addMe);
			else if (group.equalsIgnoreCase("<<triggerer>>"))         sendToUs.add(triggerer);
			else if (group.equalsIgnoreCase("<<command-triggerer>>")) sendToPlayer(message, triggerer, true, false);
			else if (group.equalsIgnoreCase("<<command-recipient>>")) flagCommand = true;
			else if (group.equalsIgnoreCase("<<say-triggerer>>"))     sendToPlayer(message, triggerer, false, true);
			else if (group.equalsIgnoreCase("<<say-recipient>>"))     flagSay     = true;
			else if (group.equalsIgnoreCase("<<command-console>>"))
				for(String command : message.split("\n")) MCServer.dispatchCommand(Console, command.replaceAll("�.", ""));
			else if (group.toLowerCase().startsWith("<<craftirc|") && CraftIRCPlugin != null)
				CraftIRCPlugin.sendMessageToTag(message, group.substring(11, group.length()-2));
			else if (group.equalsIgnoreCase("<<server>>")) {
				String [] with    = {"server", "", "", "", ""};
				String serverMessage = "[rTriggers] " + rParser.parseMessage(message, replace, with);
				for(String send : serverMessage.split("\n"))
					log.info(send);
			}
			else if (group.equalsIgnoreCase("<<twitter>>")){
				String [] with    = {"Twitter", "", "", "",""};
				String twitterMessage = rParser.parseMessage(message, replace, with);
				Plugin ServerEvents = MCServer.getPluginManager().getPlugin("ServerEvents");
				if (ServerEvents != null){
					try {
						org.bukkit.croemmich.serverevents.ServerEvents.displayMessage(twitterMessage);
					} catch (ClassCastException ex){
						log.info("[rTriggers] ServerEvents not found!");
					}
				} else  log.info("[rTriggers] ServerEvents not found!");
			} else if (group.toLowerCase().startsWith("<<player|")){
				String playerName = group.substring(9, group.length()-2);
				Player putMe = MCServer.getPlayer(playerName);
				if (putMe != null)
					sendToUs.add(putMe);
			} else if (group.equalsIgnoreCase("<<execute>>")){
				Runtime rt = Runtime.getRuntime();
				log.info("[rTriggers] Executing:" + message);
				try {
					Process pr = rt.exec(message);
				} catch (IOException e) { e.printStackTrace(); }
			}
		}
		/****************************************************
		 * List of non-special case groups has been constructed.
		 * Find all the  players who belong to the non-special
		 * case groups, and send the message to them.  */
		for (Player sendToMe : constructPlayerList(sendToGroupsFiltered.toArray(new String[sendToGroupsFiltered.size()]), sendToUs)){
			sendToPlayer(message, sendToMe, flagCommand, flagSay);
		}
	}
	/**
	 * @param groups An array of groups you want the members of
	 * @param list A list of players (may already contain players)
	 * @return A set containing players from list and players who are members of groups[]
	 */
	public Set<Player> constructPlayerList(String [] groups, HashSet<Player> list){
		if (PermissionsPlugin == null) return list;
		for (Player addMe: MCServer.getOnlinePlayers()){
			if (list.contains(addMe)) continue;
			for(String oneOfUs : groups){
				if (PermissionsPlugin.inSingleGroup(addMe.getWorld().getName(), addMe.getName(), oneOfUs)){
					list.add(addMe);
					break;
				}
			}
		}
		return list;
	}
	
	public void sendToPlayer(String message, Player recipient, boolean flagCommand, boolean flagSay) {
		String [] with = getTagReplacements(recipient);
		String [] replace = {"<<recipient>>", "<<recipient-ip>>", "<<recipient-locale>>", "<<recipient-country>>", "<<recipient-balance>>"};
		message = rParser.parseMessage(message, replace, with);
		if (flagSay)
			for(String sayThis : message.split("\n"))   recipient.chat(sayThis);
		if (!flagCommand && !flagSay)
			for(String sendMe  : message.split("\n"))   recipient.sendMessage(sendMe);
		if(flagCommand)
			for(String command : message.split("\n")) recipient.performCommand(command.replaceAll("�.", ""));
	}
}