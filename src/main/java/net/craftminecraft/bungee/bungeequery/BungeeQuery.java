package net.craftminecraft.bungee.bungeequery;

import net.md_5.bungee.api.config.ListenerInfo;
import net.md_5.bungee.api.plugin.Plugin;

/**
 * Hello world!
 *
 */
public class BungeeQuery extends Plugin {
    @Override
    public void onEnable() {
        this.getProxy().getScheduler().runAsync(this, new MinecraftQuery(this, (ListenerInfo) this.getProxy().getConfigurationAdapter().getListeners().iterator().next()));
    }
}
