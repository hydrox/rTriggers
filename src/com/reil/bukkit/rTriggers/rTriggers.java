package com.reil.bukkit.rTriggers;
import java.io.File;
import java.io.IOException;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.Server;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityListener;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.PluginEvent;
import org.bukkit.event.server.ServerListener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.croemmich.serverevents.*;

import com.nijiko.coelho.iConomy.iConomy;
import com.nijiko.permissions.PermissionHandler;
import com.nijikokun.bukkit.Permissions.Permissions;
import com.reil.bukkit.rParser.rParser;

@SuppressWarnings("unused")
public class rTriggers extends JavaPlugin {
	public rPropertiesFile Messages;
	Random RNG = new Random();
	Plugin ServerEvents;
	PlayerListener playerListener = new rTriggersPlayerListener(this);
	EntityListener entityListener = new rTriggersEntityListener(this);
	rTriggersServerListener serverListener = new rTriggersServerListener(this);
	Logger log = Logger.getLogger("Minecraft");
	Server MCServer;
	Timer scheduler;
	boolean registered = false;
	
	public boolean useiConomy = false;
	public iConomy iConomyPlugin;
	public PermissionHandler PermissionsPlugin;
    
    HashMap <String, Integer> listTracker = new HashMap<String,Integer>();
	HashMap <Integer, EntityDamageEvent.DamageCause> deathCause = new HashMap <Integer, EntityDamageEvent.DamageCause>();
	HashMap <Integer, Entity> deathBringer = new HashMap <Integer, Entity>();

    /**
     * Goes through each message in messages[] and registers events that it sees in each.
     * @param messages
     */
	public void registerEvents(String[] messages){
		if (registered) return;
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
		registered = true;
	} 
	public void onEnable(){
		MCServer = getServer();
		getDataFolder().mkdir();
        Messages = new rPropertiesFile(getDataFolder().getPath() + "/rTriggers.properties");
        if (MCServer.getPluginManager().getPlugin("Permissions") != null){
        	PermissionsPlugin = Permissions.Security;
        	log.info("[rTriggers] Attached plugin to Permissions.");
        }

		try {
			registerEvents(Messages.load());
		} catch (Exception e) {
			log.log(Level.SEVERE, "[rTriggers]: Exception while loading properties file.", e);
		}
		
		/* Go through all timer messages, create rTriggersTimers for each unique list */
		if (Messages.keyExists("<<timer>>")){
			HashMap<String, ArrayList<String>> timerLists = new HashMap <String, ArrayList<String>>();
			scheduler = new Timer();
			// Sort all the timer messages into lists 
			for (String sortMe : Messages.getStrings("<<timer>>")){
				String [] split =  sortMe.split(":");
				String [] options =  split[1].split(",");
				String sortMeList = options[0];
				if(!timerLists.containsKey(sortMeList)){
					timerLists.put(sortMeList, new ArrayList<String>());
				}
				timerLists.get(sortMeList).add(sortMe);
			}
			// Make an rTriggersTimer for each list!
			for (String key : timerLists.keySet().toArray(new String[timerLists.keySet().size()])){
				// rTriggersTimer(rTriggers rTriggers, Timer timer, String [] Messages)
				ArrayList<String> sendTheseAList = timerLists.get(key);
				String [] sendThese = sendTheseAList.toArray(new String[sendTheseAList.size()]);
				rTriggersTimer scheduleMe = new rTriggersTimer(this, scheduler, sendThese); 
				scheduler.schedule(scheduleMe, scheduleMe.delay);
			}
		}
		// Do onload events for everything that might have loaded before rTriggers
		
		serverListener.checkAlreadyLoaded();
		
		log.info("[rTriggers] Loaded: Version " + getDescription().getVersion());
	}
	
	public void onDisable(){
		Messages.save();
		if (scheduler != null) scheduler.cancel();
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
		/* Everyone triggers their own name*/
		if (triggerMessage != null){
			groupArray.add("<<player|" + triggerMessage.getName() + ">>");
			if(PermissionsPlugin != null){
				groupArray.addAll(Arrays.asList(PermissionsPlugin.getGroups(triggerMessage.getWorld().getName(),triggerMessage.getName())));
			}
			groupArray.add("<<everyone>>");
		} else {
			// If there's no player, then we have a custom trigger field
			groupArray.add("<<customtrigger>>");
		}
		
		/* Obtain list of online players */
		String playerList = new String();
		Player [] players = MCServer.getOnlinePlayers();
		if (players.length == 1)
			playerList = players[0].getDisplayName();
		else {
			StringBuilder list = new StringBuilder();
			for (Player getName : players){
				list.insert(0, getName.getDisplayName() + ", ");
			}
			playerList = list.toString();
		}
		
		/* Check for messages triggered by each group the player is a member of. */
		for (String groupName : groupArray){
			if (Messages.keyExists(groupName)){
				// Check all the messages for this group 
				for (String sendToGroups_Message : Messages.getStrings(groupName)){
					String [] split =  sendToGroups_Message.split(":");
					String [] options =  split[1].split(",");
					boolean hookValid = false;
					
					// See if any of the options of this message match the one we called the funciton with
					if (split[1].isEmpty() && option.equalsIgnoreCase("onlogin")){
						// Default case:
						// No options in the message, and we're triggerin an onlogin case.
						hookValid = true;
					} else for (int i = 0; i <options.length && hookValid == false; i++){
						// Otherwise, just check each option, see if it matches the parameter
						hookValid = options[i].equalsIgnoreCase(option);
					}
					
					// If it does match an option, we sort it out and send it
					if (hookValid) {
						
						/**************************
						 * Tag replacement start!
						 *************************/
						
						String message = rParser.combineSplit(2, split, ":");
						
						message = replaceLists(message);
						
						String [] replace = {"(?<!\\\\)@", "<<player-list>>", "(?<!\\\\)&", "<<color>>" /*,"<<triggerer-color>>"*/,"<<placeholder>>"};
						String [] with    = {"\n�f"      , playerList       , "�"         , "�"/*,triggerMessage.getColor(),*/    ,""};
						message = rParser.replaceWords(message, replace, with);
						
						String [] with2    = getTagReplacements(triggerMessage);
						String [] replace2 = { "<<triggerer>>"          , "<<triggerer-ip>>"    , "<<triggerer-locale>>", "<<triggerer-country>>", "<<triggerer-balance>>" };
						message = rParser.replaceWords(message, replace2, with2);
						
						
						if (eventToReplace.length > 0)
							message = rParser.replaceWords(message, eventToReplace, eventReplaceWith);
						/**************************
						 *  Tag replacement end! */
						
						sendMessage(message, triggerMessage, split[0]);
						triggeredMessage = true;
					}
				}
			}
		}
		return triggeredMessage;
	}
	
	public String replaceLists(String message) {
		int optionStart;
		int optionEnd;
		String listMember;
		while ( (optionStart = message.indexOf("<<list|")) != -1){
			optionStart += "<<list|".length();
			optionEnd = message.indexOf(">>", optionStart);
			String options = message.substring(optionStart, optionEnd);
			String [] optionSplit = options.split("\\|");
			// Call up the list
			String getThis = "<<list|" + optionSplit[0] + ">>";
			String [] messageList = Messages.getStrings(getThis);
			if (messageList.length > 0){
				if (optionSplit.length > 1 && optionSplit[1].equalsIgnoreCase("rand")){
						listMember = messageList[RNG.nextInt(messageList.length)];
				} else {
					if(!listTracker.containsKey(optionSplit[0])){
						listTracker.put(optionSplit[0], 0);
					}
					int listNumber = listTracker.get(optionSplit[0]);
					listMember = messageList[listNumber];
					listTracker.put(optionSplit[0], (listNumber + 1)%messageList.length);
				}
			} else listMember = "";
			message = message.replace("<<list|" + options + ">>", listMember);
		}
		return message;
	}
	/**
	 * Use in conjunction with rParser.replaceWords or rParser.parseMessage;
	 * @param player A player to get the replacements for
	 * @return Array of things to replace tags in this order: Name, IP address, locale, country, iConomy balance
	 */
	public String[] getTagReplacements(Player player){
		if (player == null){
			String [] returnArray = {"", "", "", "", ""};
			return returnArray;
		}
		// Get <<triggerer-balance>> tag
		double balance = 0;
		if (useiConomy){
			if (iConomy.getBank().hasAccount(player.getName())) {
				balance = iConomy.getBank().getAccount(player.getName()).getBalance();
			}
		}
		
		// Get <<triggerer-ip>> and <<triggerer-locale>> tags
		InetSocketAddress triggerIP = player.getAddress();
		String triggerCountry;
		String triggerLocale;
		try {
			Locale playersHere = net.sf.javainetlocator.InetAddressLocator.getLocale(triggerIP.getAddress());
			triggerCountry = playersHere.getDisplayCountry();
			triggerLocale = playersHere.getDisplayName();
		} catch (net.sf.javainetlocator.InetAddressLocatorException e) {
			e.printStackTrace();
			triggerCountry = "";
			triggerLocale = "";
		} catch (NoClassDefFoundError e){
			triggerCountry = ""; 
			triggerLocale = "";
		}
		String [] returnArray = { player.getName(), triggerIP.toString(), triggerLocale, triggerCountry, Double.toString(balance)};
		return returnArray; 
	}

	private void sendMessage(String message, Player triggerMessage, String Groups){
		/* Default: Send to player unless other groups are specified.
		 * If so, send to those instead. */
		if (Groups.isEmpty() || Groups.equalsIgnoreCase("<<triggerer>>")) {
			sendToPlayer(message, triggerMessage, false);
		}
		else {
			String [] sendToGroups = Groups.split(",");
			sendToGroups(sendToGroups, message, triggerMessage);
		}
	}

	/**
	 * Takes care of 'psuedo-groups' like <<triggerer>>, <<server>>, and <<everyone>>,
	 * then sends to the rest of the normal groups.
	 * @param sendToGroups An array of groups and pseudo-groups to send this message to
	 * @param message The message you want to send
	 * @param triggerer The player that triggered this message (can be null, if no triggerer)
	 */
	public void sendToGroups (String [] sendToGroups, String message, Player triggerer) {
		ArrayList <String> sendToGroupsFiltered = new ArrayList<String>();
		HashSet <Player> sendToUs = new HashSet<Player>();
		boolean flagEveryone = false;
		boolean flagCommand = false;
		/*************************************
		 * Begin:
		 * 1) Constructing list of groups to send to
		 * 2) Processing 'special' groups (ones in double-chevrons) */
		for (String group : sendToGroups){
			/*************************
			 * Special cases start! */
			if (group.equalsIgnoreCase("<<triggerer>>")) {
				if (triggerer != null){
					sendToUs.add(triggerer);
				}
			} else if (group.equalsIgnoreCase("<<command-triggerer>>")){
				sendToPlayer(message, triggerer, true);
			} else if (group.equalsIgnoreCase("<<command-recipient>>")){
				flagCommand = true;
			} else if (group.equalsIgnoreCase("<<everyone>>")){
				sendToUs.clear();
				for (Player addMe : MCServer.getOnlinePlayers())
					sendToUs.add(addMe);
				flagEveryone = true;
			} else if (group.equalsIgnoreCase("<<server>>")) {
				String [] replace = {"<<recipient>>", "<<recipient-ip>>", "<<recipient-color>>", "<<recipient-balance>>", "�"};
				String [] with    = {"server", "", "", "", ""};
				String serverMessage = "[rTriggers] " + rParser.parseMessage(message, replace, with);
				for(String send : serverMessage.split("\n"))
					log.info(send);
			}
			else if (group.equalsIgnoreCase("<<twitter>>")){
				String [] replace = {"<<recipient>>", "<<recipient-ip>>", "<<recipient-color>>", "<<recipient-balance>>"};
				String [] with    = {"Twitter", "", "", ""};
				String twitterMessage = rParser.parseMessage(message, replace, with);
				Plugin ServerEvents = MCServer.getPluginManager().getPlugin("ServerEvents");
				if (ServerEvents != null){
					try {
						org.bukkit.croemmich.serverevents.ServerEvents.displayMessage(twitterMessage);
					} catch (ClassCastException ex){
						log.info("[rTriggers] ServerEvents not found!");
					}
				} else {
					log.info("[rTriggers] ServerEvents not found!");
				}
			} else if (group.startsWith("<<player|")){
				String playerName = group.substring(9, group.length()-2);
				log.info(playerName);
				Player putMe = MCServer.getPlayer(playerName);
				if (putMe != null)
					sendToUs.add(putMe);
			} else if (group.equalsIgnoreCase("<<execute>>")){
				Runtime rt = Runtime.getRuntime();
				log.info("[rTriggers] Executing:" + message);
				try {
					Process pr = rt.exec(message);
				} catch (IOException e) { 
					e.printStackTrace();
				}
			}
			/**********************
			 * Special cases end!*/
			else {
				sendToGroupsFiltered.add(group);
			}
		}
		/****************************************************
		 * List of non-special case groups has been constructed.
		 * Find all the  players who belong to the non-special
		 * case groups, and send the message to them.  */
		for (Player sendToMe : constructPlayerList(sendToGroupsFiltered.toArray(new String[sendToGroupsFiltered.size()]), sendToUs)){
			sendToPlayer(message, sendToMe, flagCommand);
		}
	}
	/**
	 * @param groups An array of groups you want the members of
	 * @param list A list of players (may already contain players)
	 * @return A set containing players from list and players who are members of groups[]
	 */
	public Set<Player> constructPlayerList(String [] groups, HashSet<Player> list){
		for (Player addMe: MCServer.getOnlinePlayers()){
			if (PermissionsPlugin != null && !list.contains(addMe)){
				search:
					for(String oneOfUs : groups){
						if (PermissionsPlugin.inSingleGroup(addMe.getWorld().getName(), addMe.getName(), oneOfUs)){
							list.add(addMe);
							break search;
						}
					}
			}
		}
		return list;
	}
	
	public void sendToPlayer(String message, Player recipient, boolean flagCommand) {
		String [] with = getTagReplacements(recipient);
		String [] replace = {"<<recipient>>", "<<recipient-ip>>", "<<recipient-locale>>", "<<recipient-country>>", "<<recipient-balance>>"};
		message = rParser.parseMessage(message, replace, with);
		if (flagCommand == false){
			for(String send : message.split("\n"))
				recipient.sendMessage(send);
		} else {
			recipient.performCommand(message);
		}
	}
}