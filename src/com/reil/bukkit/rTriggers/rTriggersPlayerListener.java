package com.reil.bukkit.rTriggers;
import java.util.LinkedList;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.event.player.*;


public class rTriggersPlayerListener extends PlayerListener {
	private final rTriggers plugin;

	/**
	 * @param rTriggers
	 */
	rTriggersPlayerListener(rTriggers rTriggers) {
		plugin = rTriggers;
	}
	
	@Override
	public void onPlayerBedEnter(PlayerBedEnterEvent event){
		plugin.triggerMessages(event.getPlayer(), "onbedenter");
		if (plugin.triggerMessages(event.getPlayer(), "onbedenter|override")) event.setCancelled(true);
	}
	@Override
	public void onPlayerBedLeave(PlayerBedLeaveEvent event){
		this.plugin.triggerMessages(event.getPlayer(), "onbedleave");
	}
	
	@Override
	public void onPlayerJoin(PlayerJoinEvent event){
		Player triggerMessage = event.getPlayer();
		plugin.triggerMessages(triggerMessage, "onlogin");
		if (plugin.triggerMessages(triggerMessage, "onlogin|override")){
			event.setJoinMessage("");
		}
		return;
	}
	
	@Override
	public void onPlayerQuit(PlayerQuitEvent event){
		Player triggerMessage = event.getPlayer();
		plugin.triggerMessages(triggerMessage, "ondisconnect");
		if (plugin.triggerMessages(triggerMessage, "ondisconnect|override")){
			event.setQuitMessage("");
		}
		plugin.deathCause.remove(triggerMessage.getEntityId());
		plugin.deathBringer.remove(triggerMessage.getEntityId());
		return;
	}
	
	@Override
	public void onPlayerKick(PlayerKickEvent event){
		Player triggerMessage = event.getPlayer();
		String [] replaceThese = {"<<kick-reason>>" , "<<kickedplayer>>"     };
		String [] withThese =    {event.getReason() , triggerMessage.getName()};
		plugin.triggerMessages(triggerMessage, "onkick", replaceThese, withThese);
	}
	
	@Override
	public void onPlayerRespawn(PlayerRespawnEvent event){
		plugin.triggerMessages(event.getPlayer(), "onrespawn");
	}
	
	@Override
	public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event){
		Player player = event.getPlayer();
		
		String [] split = event.getMessage().split(" ");
		int numParams = split.length - 1;
		if(! (plugin.optionsMap.containsKey("oncommand|" + split[0]) ||
				plugin.optionsMap.containsKey("oncommand|" + split[0] + "|" + numParams) ||
				plugin.optionsMap.containsKey("oncommand|" + split[0] + "|override") ||
				plugin.optionsMap.containsKey("oncommand|" + split[0] + "|override|" + numParams)
				)) return;
		
		List<String> replaceThese = new LinkedList<String>();
		List<String> withThese    = new LinkedList<String>();
		/* Build parameter list */
		StringBuilder params = new StringBuilder();
		StringBuilder reverseParams = new StringBuilder();
		String prefix = ""; 
		int max = split.length;
		for(int i = 1; i < max; i++){
			params.append(prefix + split[i]);
			reverseParams.insert(0, split[max - i] + prefix);
			prefix = " ";
			
			replaceThese.add("<<param" + Integer.toString(i) + ">>");
			withThese.add(split[i]);
			
			replaceThese.add("<<param" + Integer.toString(i) + "->>");
			withThese.add(params.toString());

			replaceThese.add("<<param" + Integer.toString(max - i) + "\\+>>");
			withThese.add(reverseParams.toString());
		}
		replaceThese.add("<<params>>");
		withThese.add(params.toString());
		String [] replaceTheseArray = replaceThese.toArray(new String[replaceThese.size()]);
		String [] withTheseArray = withThese.toArray(new String[withThese.size()]);

		plugin.triggerMessages(player, "oncommand|" + split[0], replaceTheseArray, withTheseArray);
		
		
        if (split[0].equalsIgnoreCase("/rTriggers")) {
			plugin.triggerMessages(player, "onrTriggers", replaceTheseArray, withTheseArray);
			event.setCancelled(true);
		}
        
        if (plugin.triggerMessages(player, "oncommand|" + split[0] + "|override", replaceTheseArray, withTheseArray)
        		|| plugin.triggerMessages(player, "oncommand|" + split[0] + "|override|" + numParams, replaceTheseArray, withTheseArray)){
        	event.setCancelled(true);
        }
		
		return; 
	}
}