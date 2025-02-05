package me.limeglass.skacket.elements.sections;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.eclipse.jdt.annotation.Nullable;

import com.google.common.collect.Lists;

import ch.njol.skript.Skript;
import ch.njol.skript.config.SectionNode;
import ch.njol.skript.doc.Name;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.Section;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.Trigger;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.skript.log.ErrorQuality;
import ch.njol.skript.variables.Variables;
import ch.njol.util.Kleenean;
import me.limeglass.skacket.Skacket;
import me.limeglass.skacket.events.AnvilGUIEvent;
import me.limeglass.skacket.events.AnvilGUIEvent.Click;
import net.wesjd.anvilgui.AnvilGUI;
import net.wesjd.anvilgui.AnvilGUI.Response;

@Name("Open Anvil")
public class SecOpenAnvil extends Section {

	static {
		Skript.registerSection(SecOpenAnvil.class, "open [an] anvil [gui] (named|with title) %string% to %players% with left item %itemstack% [and [right] item %-itemstack%]",
				"open [an] anvil [gui] (named|with title) %string% to %players% with [items] %itemstacks%",
				"open [an] anvil [gui] (named|with title) %string% to %players% with left item %itemstack% [and [right] item %-itemstack%] [[and] exclud(e|ing) left and right clicks]");
	}

	private Expression<ItemStack> left, right, items;
	private Expression<Player> players;
	private Expression<String> title;
	private boolean excludes;

	@Nullable
	private TriggerItem actualNext;
	@Nullable
	private Trigger trigger;

	@SuppressWarnings("unchecked")
	@Override
	public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean isDelayed, ParseResult parseResult, SectionNode sectionNode, List<TriggerItem> triggerItems) {
		title = (Expression<String>) expressions[0];
		players = (Expression<Player>) expressions[1];
		if (matchedPattern == 1) {
			items = (Expression<ItemStack>) expressions[2];
			if (items == null)
				Skript.error("Items must be defined in the anvil expression.", ErrorQuality.SEMANTIC_ERROR);
		} else {
			left = (Expression<ItemStack>) expressions[2];
			right = (Expression<ItemStack>) expressions[3];
			if (left == null)
				Skript.error("Left item must be defined in the anvil expression.", ErrorQuality.SEMANTIC_ERROR);
		}
		excludes = matchedPattern == 2;
		trigger = loadCode(sectionNode, "anvil", AnvilGUIEvent.class);
		return true;
	}

	@Override
	protected @Nullable TriggerItem walk(Event event) {
		List<ItemStack> items = new ArrayList<>();
		if (this.items != null) {
			items.addAll(Lists.newArrayList(this.items.getArray(event)));
		} else {
			items.add(left.getSingle(event));
			if (right != null)
				items.add(right.getSingle(event));
		}
		if (items.isEmpty()) {
			debug(event, true);
			return actualNext;
		}
		AnvilGUI.Builder builder = new AnvilGUI.Builder().title(title.getSingle(event)).plugin(Skacket.getInstance());
		if (items.size() >= 1)
			builder.itemLeft(items.get(0));
		if (items.size() >= 2)
			builder.itemRight(items.get(1));
		Object localVariables = Variables.copyLocalVariables(event);
		builder.onComplete((player, text) -> {
			AnvilGUIEvent anvil = new AnvilGUIEvent(player, text, Click.COMPLETE);
			if (localVariables != null)
				Variables.setLocalVariables(anvil, localVariables);
			Bukkit.getPluginManager().callEvent(anvil);
			trigger.execute(anvil);
			return Response.close();
		});
		builder.onLeftInputClick((player) -> {
			AnvilGUIEvent anvil = new AnvilGUIEvent(player, null, Click.LEFT);
			if (localVariables != null)
				Variables.setLocalVariables(anvil, localVariables);
			Bukkit.getPluginManager().callEvent(anvil);
			if (!excludes)
				trigger.execute(anvil);
		});
		builder.onRightInputClick((player) -> {
			AnvilGUIEvent anvil = new AnvilGUIEvent(player, null, Click.RIGHT);
			if (localVariables != null)
				Variables.setLocalVariables(anvil, localVariables);
			Bukkit.getPluginManager().callEvent(anvil);
			if (!excludes)
				trigger.execute(anvil);
		});
		for (Player player : players.getArray(event))
			builder.open(player);
		debug(event, true);
		return actualNext;
	}

	@Override
	public SecOpenAnvil setNext(@Nullable TriggerItem next) {
		actualNext = next;
		return this;
	}

	@Override
	public String toString(@Nullable Event event, boolean debug) {
		if (event == null)
			return "open anvil";
		String items = this.items != null ? " with items " + Arrays.toString(this.items.getArray(event)) : this.left != null ? " with left item " + left.toString(event, debug) : "";
		return "open anvil to " + players.toString(event, debug) + items;
	}

}
