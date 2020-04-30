package me.limeglass.skacket.listeners;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.block.SignChangeEvent;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.BlockPosition;

import me.limeglass.skacket.Skacket;
import me.limeglass.skacket.events.ServerSignChangeEvent;
import me.limeglass.skacket.events.SteerVehicleEvent;
import me.limeglass.skacket.events.SteerVehicleEvent.Movement;
import me.limeglass.skacket.wrappers.WrapperPlayClientUpdateSign;
import me.limeglass.skacket.wrappers.WrapperPlayServerUpdateSign;

@SuppressWarnings("deprecation")
public class PacketListeners {

	static {
		Skacket instance = Skacket.getInstance();
		instance.getProtocolManager().addPacketListener(new PacketAdapter(instance, PacketType.Play.Client.STEER_VEHICLE) {
			@Override
			public void onPacketReceiving(PacketEvent event) {
				PacketContainer packet = event.getPacket();
				Set<Movement> movements = new HashSet<>();
				StructureModifier<Float> floats = packet.getFloat();
				float sideways = floats.read(0);
				if (sideways != 0)
					movements.add(sideways > 0 ? Movement.LEFT : Movement.RIGHT);
				float forwards = floats.read(1);
				if (forwards != 0)
					movements.add(forwards > 0 ? Movement.FORWARDS : Movement.BACKWARDS);
				StructureModifier<Boolean> booleans = packet.getBooleans();
				if (booleans.read(0))
					movements.add(Movement.JUMP);
				if (booleans.read(1))
					movements.add(Movement.UNMOUNT);
				if (movements.isEmpty())
					return;
				SteerVehicleEvent steer = new SteerVehicleEvent(event.getPlayer(), movements.toArray(new Movement[movements.size()]));
				Bukkit.getPluginManager().callEvent(steer);
				if (steer.isCancelled())
					event.setCancelled(true);
			}
		});
		// Deprecation fix in WrapperPlayServerUpdateSign when removed.
		instance.getProtocolManager().addPacketListener(new PacketAdapter(instance, PacketType.Play.Server.UPDATE_SIGN) {
			@Override
			public void onPacketReceiving(PacketEvent event) {
				WrapperPlayServerUpdateSign packet = new WrapperPlayServerUpdateSign(event.getPacket());
				String[] lines = Arrays.stream(packet.getLines())
						.map(component -> component.toString())
						.toArray(String[]::new);
				Player player = event.getPlayer();
				BlockPosition position = packet.getLocation();
				if (position == null)
					return;
				Location location = position.toLocation(player.getWorld());
				ServerSignChangeEvent signEvent = new ServerSignChangeEvent(location.getBlock(), event.getPlayer(), lines);
				Bukkit.getPluginManager().callEvent(signEvent);
				if (event.isCancelled()) {
					event.setCancelled(true);
					return;
				}
			}
		});
		instance.getProtocolManager().addPacketListener(new PacketAdapter(instance, PacketType.Play.Client.UPDATE_SIGN) {
			@Override
			public void onPacketReceiving(PacketEvent event) {
				WrapperPlayClientUpdateSign packet = new WrapperPlayClientUpdateSign(event.getPacket());
				String[] lines = packet.getLines();
				Player player = event.getPlayer();
				BlockPosition position = packet.getLocation();
				if (position == null)
					return;
				Location location = position.toLocation(player.getWorld());
				instance.getSignManager().getSignFor(player).ifPresent(sign -> {
					Bukkit.getScheduler().runTask(instance, () -> {
						SignChangeEvent signEvent = new SignChangeEvent(location.getBlock(), event.getPlayer(), lines);
						if (instance.getConfig().getBoolean("signgui-call-change-event", false)) {
							Bukkit.getPluginManager().callEvent(signEvent);
							if (event.isCancelled()) {
								event.setCancelled(true);
								return;
							}
						}
						sign.getPreviousBlockData(player).ifPresent(blockData -> player.sendBlockChange(location, blockData));
						sign.accept(signEvent);
					});
				});
			}
		});
	}

}
