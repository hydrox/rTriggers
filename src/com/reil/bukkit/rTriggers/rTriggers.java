package com.reil.bukkit.rTriggers;
import java.io.File;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Timer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.Server;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityListener;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.server.ServerListener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.croemmich.serverevents.*;

import com.nijikokun.bukkit.iConomy.iConomy;
import com.reil.bukkit.rParser.rParser;

@SuppressWarnings("unused")
public class rTriggers extends JavaPlugin {
	public rPropertiesFile Messages;
	Plugin ServerEvents;
	PlayerListener playerListener = new rTriggersPlayerListener(this);
	EntityListener entityListener = new rTriggersEntityListener(this);
	ServerListener serverListener = new rTriggersServerListener(this);
	Logger log = Logger.getLogger("Minecraft");
	Server MCServer = getServer();
	Timer scheduler;
	
	
	String defaultGroup = "default";
	String versionNumber = "0.6_4";
	
	private boolean useiConomy = false;

    public boolean checkiConomy() {
        Plugin test = this.getServer().getPluginManager().getPlugin("iConomy");

        if (test != null) {
            this.useiConomy = true;
        } else {
            this.useiConomy = false;
        }

        return useiConomy;
    }
	
    public rTriggers(PluginLoader pluginLoader, Server instance, PluginDescriptionFile desc,File folder, File plugin, ClassLoader cLoader) {
        super(pluginLoader, instance, desc, folder, plugin, cLoader);
        folder.mkdir();
        Messages = new rPropertiesFile(folder.getPath() + "/rTriggers.properties");
        registerEvents();
    }
	
	public void registerEvents(){
		PluginManager loader = MCServer.getPluginManager();
		/* TODO (Efficiency): Go through each message, see if any messages actually need these listeners. */
		// Regex: ^([A-Za-z0-9,]+):([A-Za-z0-9,]*:([A-Za-z0-9,]*disconnect([A-Za-z0-9,]*)
		loader.registerEvent(Event.Type.PLAYER_JOIN, playerListener, Event.Priority.Monitor, this);
		loader.registerEvent(Event.Type.PLAYER_QUIT, playerListener, Event.Priority.Monitor, this);
		loader.registerEvent(Event.Type.PLAYER_COMMAND, playerListener, Event.Priority.Monitor, this);
		loader.registerEvent(Event.Type.PLAYER_KICK, playerListener, Event.Priority.Monitor, this);
		loader.registerEvent(Event.Type.SERVER_COMMAND, serverListener, Event.Priority.Monitor, this);
		loader.registerEvent(Event.Type.ENTITY_DEATH, entityListener, Event.Priority.Monitor, this);
		loader.registerEvent(Event.Type.ENTITY_DAMAGED, entityListener, Event.Priority.Monitor, this);
		loader.registerEvent(Event.Type.ENTITY_DAMAGEDBY_BLOCK, entityListener, Event.Priority.Monitor, this);
		loader.registerEvent(Event.Type.ENTITY_DAMAGEDBY_ENTITY, entityListener, Event.Priority.Monitor, this);
		loader.registerEvent(Event.Type.ENTITY_DAMAGEDBY_PROJECTILE, entityListener, Event.Priority.Monitor, this);
	} 
	public void onEnable(){
		/*
		 * TODO: When we get groups again, finally.
		if (etc.getDataSource().getDefaultGroup() != null)
			defaultGroup = etc.getDataSource().getDefaultGroup().Name;*/
		checkiConomy();
		
		try {
			Messages.load();
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
		log.info("[rTriggers] Loaded: Version " + versionNumber);
	}
	public void onDisable(){
		Messages.save();
		if (scheduler != null) scheduler.cancel();
		log.info("[rTriggers] Disabled!");
	} 
	
	
	/* Looks through all of the messages,
	 * Sends the messages triggered by groups which 'triggerMessage' is a member of,
	 * But only if that message has the contents of 'option' as one of its options */
	public void triggerMessagesWithOption(Player triggerMessage, String option){
		String[] eventToReplace = new String[0];
		String[] eventReplaceWith = new String[0];
		triggerMessagesWithOption(triggerMessage, option, eventToReplace, eventReplaceWith);
	}
	
	public void triggerMessagesWithOption(Player triggerMessage, String option, String[] eventToReplace, String[] eventReplaceWith){
		ArrayList<String>groupArray = new ArrayList<String>();
		/* Obtain triggerer's group list */
		/* TODO: Reimpliment when Groups come back
		if (triggerMessage.hasNoGroups()){
			groupArray.add(defaultGroup);
		} else {
			groupArray.addAll(Arrays.asList(triggerMessage.getGroups()));
		}*/
		groupArray.add("<<player|" + triggerMessage.getName() + ">>");
		groupArray.add("<<everyone>>");
		
		/* Obtain list of online players */
		String playerList = new String();
		Player [] players = MCServer.getOnlinePlayers();
		if (players.length == 1)
			playerList = players[0].getName();
		else {
			StringBuilder list = new StringBuilder();
			for (Player getName : players){
				list.insert(0, getName.getName() + ", ");
			}
			playerList = list.toString();
		}
		
		/* Check for messages triggered by each group the player is a member of. */
		for (String groupName : groupArray){
			if (Messages.keyExists(groupName)){
				for (String sendToGroups_Message : Messages.getStrings(groupName)){
					String [] split =  sendToGroups_Message.split(":");
					String [] options =  split[1].split(",");
					boolean hookValid = false;
					
					if (split[1].isEmpty() && option.equalsIgnoreCase("onlogin")){
						hookValid = true;
					} else for (int i = 0; i <options.length && hookValid == false; i++){
						if(options[i].equalsIgnoreCase(option)) hookValid = true;
					}
					
					if (hookValid) {
						String message = rParser.combineSplit(2, split, ":");
						
						/**************************************************
						 *  Tag replacement: First round (triggerer) go! */
						int balance = 0;
						if (useiConomy){
							if (iConomy.db.has_balance(triggerMessage.getName())) {
								balance = iConomy.db.get_balance(triggerMessage.getName());
							}
						}
						InetSocketAddress triggerIP = triggerMessage.getAddress();
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
						String [] replace = {"@"	, "<<triggerer>>"          , "<<triggerer-ip>>"    , "<<triggerer-locale>>", "<<triggerer-country>>", "<<triggerer-balance>>"  , "<<player-list>>", "<<color>>" /*,"<<triggerer-color>>"*/};
						String [] with    = {"\n"	, triggerMessage.getName() , triggerIP.toString()  ,         triggerLocale,           triggerCountry, Integer.toString(balance), playerList       , "�"/*,triggerMessage.getColor(),*/};					
						message = rParser.parseMessage(message, replace, with);
						if (eventToReplace.length > 0)
							message = rParser.parseMessage(message, eventToReplace, eventReplaceWith);
						/**************************
						 *  Tag replacement end! */
						
						sendMessage(message, triggerMessage, split[0]);
					}
				}
			}
		}
	}
	
	
	public void sendMessage(String message, Player triggerMessage, String Groups){
		/* Default: Send to player unless groups are specified.
		 * If so, send to those instead. */
		if (Groups.isEmpty()) {
			sendToPlayer(message, triggerMessage, false);
		}
		else {
			String [] sendToGroups = Groups.split(",");
			sendToGroups(sendToGroups, message, triggerMessage);
		}
	}

	/* Takes care of 'psuedo-groups' like <<triggerer>>, <<server>>, and <<everyone>>,
	 * then sends to the rest as normal */
	public void sendToGroups (String [] sendToGroups, String message, Player triggerer) {
		ArrayList <String> sendToGroupsFiltered = new ArrayList<String>();
		HashMap <Player, Player> sendToUs = new HashMap<Player, Player>();
		boolean flagEveryone = false;
		boolean flagCommand = false;
		/*************************************
		 * Begin:
		 * 1) Constructing list of groups to send to
		 * 2) Processing 'special' groups (ones in double-chevrons) */
		for (String group : sendToGroups){
			if (group.equalsIgnoreCase("<<triggerer>>")) {
				if (triggerer != null){
					sendToUs.put(triggerer, triggerer);
				}
			} else if (group.equalsIgnoreCase("<<command-triggerer>>")){
				sendToPlayer(message, triggerer, true);
			} else if (group.equalsIgnoreCase("<<command-recipient>>")){
				flagCommand = true;
			} else if (group.equalsIgnoreCase("<<everyone>>")){
				sendToUs.clear();
				for (Player addMe : MCServer.getOnlinePlayers())
					sendToUs.put(addMe, addMe);
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
			} else if (group.substring(0,9).equalsIgnoreCase("<<player|")){
				String playerName = group.substring(9, group.length()-2);
				log.info(playerName);
				Player putMe = MCServer.getPlayer(playerName);
				if (putMe != null)
					sendToUs.put(putMe, putMe);
			} else {
				sendToGroupsFiltered.add(group);
			}
		}
		/****************************************************
		 * List of non-special case groups is now constructed.
		 * Find players who belong to the non-special case groups,
		 * and send the message to them.  */
		for (Player sendToMe : constructPlayerList(sendToGroupsFiltered.toArray(new String[sendToGroupsFiltered.size()]), sendToUs).values()){
			sendToPlayer(message, sendToMe, flagCommand);
		}
	}
	
	public HashMap<Player, Player> constructPlayerList(String [] inTheseGroups, HashMap<Player,Player> List){
		for (Player addMe: MCServer.getOnlinePlayers()){
			if (!List.containsKey(addMe)){
				/*
				 * TODO: Reimplement this when groups come back.
				if (addMe.hasNoGroups()) {
					search:
					for (String isDefault : inTheseGroups) {
						if (isDefault.equalsIgnoreCase(defaultGroup)) {
							List.put(addMe,addMe);
						}
						break search;
					}
				} else {
					search:
					for(String memberGroup : addMe.getGroups()) {
						for(String amIHere : inTheseGroups){
							if (memberGroup.equalsIgnoreCase(amIHere)){
								List.put(addMe, addMe);
								break search;
							}
						}
					}
				}
				*/
			}
		}
		return List;
	}
	
	public void sendToPlayer(String message, Player recipient, boolean flagCommand) {
		int balance = 0;
		if (useiConomy){
			if (iConomy.db.has_balance(recipient.getName())) {
				balance = iConomy.db.get_balance(recipient.getName());
			}
		}
		/****************************************************
		 * Tag replacement: Second round (recipient)!  Go!  */
		InetSocketAddress recipientIP = recipient.getAddress(); 
		String recipientCountry;
		String recipientLocale;
		try {
			Locale playersHere = net.sf.javainetlocator.InetAddressLocator.getLocale(recipientIP.getAddress());
			recipientCountry = playersHere.getDisplayCountry();
			recipientLocale = playersHere.getDisplayName();
		} catch (net.sf.javainetlocator.InetAddressLocatorException e) {
			e.printStackTrace();
			recipientCountry = "";
			recipientLocale = "";
		} catch (NoClassDefFoundError e){
			recipientCountry = "";
			recipientLocale = "";
		}
		String [] replace = {"<<recipient>>"    , "<<recipient-ip>>"    , "recipient-locale", "<<recipient-country>>", "<<recipient-color>>", "<<recipient-balance>>"};
		String [] with    = {recipient.getName(), recipientIP.toString(), recipientLocale   , recipientCountry       , ""/*recipient.getColor()*/ , Integer.toString(balance)};
		message = rParser.parseMessage(message, replace, with);
		/*************************
		 * Tag replacement end. */
		if (flagCommand == false){
			for(String send : message.split("\n"))
				recipient.sendMessage(send);
		} else {
			recipient.performCommand(message);
		}
	}
}