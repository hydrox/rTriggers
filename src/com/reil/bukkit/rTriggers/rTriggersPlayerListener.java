package com.reil.bukkit.rTriggers;
import java.util.LinkedList;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.event.player.*;


public class rTriggersPlayerListener extends PlayerListener {
	private final rTriggers rTriggers;

	/**
	 * @param rTriggers
	 */
	rTriggersPlayerListener(rTriggers rTriggers) {
		this.rTriggers = rTriggers;
	}
	
	@Override
	public void onPlayerJoin(PlayerJoinEvent event){
		Player triggerMessage = event.getPlayer();
		this.rTriggers.triggerMessages(triggerMessage, "onlogin");
		if (this.rTriggers.triggerMessages(triggerMessage, "onlogin|override")){
			event.setJoinMessage("");
		}
		return;
	}
	
	@Override
	public void onPlayerQuit(PlayerQuitEvent event){
		Player triggerMessage = event.getPlayer();
		this.rTriggers.triggerMessages(triggerMessage, "ondisconnect");
		if (this.rTriggers.triggerMessages(triggerMessage, "ondisconnect|override")){
			event.setQuitMessage("");
		}
		this.rTriggers.deathCause.remove(triggerMessage.getEntityId());
		this.rTriggers.deathBringer.remove(triggerMessage.getEntityId());
		return;
	}
	
	@Override
	public void onPlayerKick(PlayerKickEvent event){
		Player triggerMessage = event.getPlayer();
		String [] replaceThese = {"<<kick-reason>>" , "<<kickedplayer>>"     };
		String [] withThese =    {event.getReason() , triggerMessage.getName()};
		this.rTriggers.triggerMessages(triggerMessage, "onkick", replaceThese, withThese);
	}
	
	@Override
	public void onPlayerRespawn(PlayerRespawnEvent event){
		this.rTriggers.triggerMessages(event.getPlayer(), "onrespawn");
	}
	
	@Override
	public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event){
		Player player = event.getPlayer();
		
		String [] split = event.getMessage().split(" ");
		if(! (rTriggers.optionsMap.containsKey("oncommand|" + split[0])
				|| rTriggers.optionsMap.containsKey("oncommand|" + split[0] + "|override"))) return;
		
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

		this.rTriggers.triggerMessages(player, "oncommand|" + split[0], replaceTheseArray, withTheseArray);
		
		
        if (split[0].equalsIgnoreCase("/rTriggers")) {
			this.rTriggers.triggerMessages(player, "onrTriggers", replaceTheseArray, withTheseArray);
			event.setCancelled(true);
		}
        
        if (this.rTriggers.triggerMessages(player, "oncommand|" + split[0] + "|override", replaceTheseArray, withTheseArray)){
        	event.setCancelled(true);
        }
		
		return; 
	}
}