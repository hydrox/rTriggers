package com.reil.bukkit.rTriggers;

import java.util.HashSet;

import org.bukkit.event.server.*;
import org.bukkit.plugin.*;

public class rTriggersServerListener extends ServerListener {
	rTriggers plugin;
	HashSet<String> watchPlugins = new HashSet<String>();
	
	rTriggersServerListener(rTriggers rTriggers){
		this.plugin = rTriggers;
	}
	@Override
	public void onServerCommand(ServerCommandEvent event){
		plugin.triggerMessages("onconsole");
	}
	public void listenFor(String pluginName) {
		watchPlugins.add(pluginName);
	}
	
	public void checkAlreadyLoaded(PluginManager PM) {
		for(String checkMe:watchPlugins)
			if(PM.getPlugin(checkMe) != null) plugin.triggerMessages("onload|" + checkMe);
	}	
	
	@Override
    public void onPluginEnable(PluginEnableEvent event) {
        plugin.grabPlugins(plugin.pluginManager);
        
        String pluginName = event.getPlugin().getDescription().getName();
        if(watchPlugins.contains(pluginName)) plugin.triggerMessages("onload|" + pluginName);
    }
	
	@Override
    public void onPluginDisable(PluginDisableEvent event) {
		if (plugin.PermissionsPlugin != null) {
            if (event.getPlugin().getDescription().getName().equals("Permissions")) {
            	plugin.PermissionsPlugin = null;
                System.out.println("[rTriggers] Unattached from Permissions.");
            }
        }
    }
}
