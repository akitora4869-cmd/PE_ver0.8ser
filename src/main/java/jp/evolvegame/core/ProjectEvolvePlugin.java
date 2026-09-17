package jp.evolvegame.core;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ProjectEvolvePlugin extends JavaPlugin {
    private MatchManager matchManager;
    private TestMonsterController testMonsterController;
    private MonsterGameplayController monsterGameplayController;
    private MonsterAbilityController monsterAbilityController;
    private MonsterSkillController monsterSkillController;
    private HunterCombatController hunterCombatController;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.matchManager = new MatchManager(this);
        this.testMonsterController = new TestMonsterController(this, matchManager);
        this.monsterGameplayController = new MonsterGameplayController(this, matchManager, testMonsterController);
        this.monsterAbilityController = new MonsterAbilityController(this, matchManager, testMonsterController);
        this.monsterSkillController = new MonsterSkillController(this, matchManager, monsterAbilityController);
        this.matchManager.setMonsterSkillController(monsterSkillController);
        this.hunterCombatController = new HunterCombatController(this, matchManager);
        this.matchManager.setHunterCombatController(hunterCombatController);

        EvolveCommand command = new EvolveCommand(this, matchManager, testMonsterController, monsterGameplayController);
        PluginCommand evolve = getCommand("evolve");
        if (evolve != null) {
            evolve.setExecutor(command);
            evolve.setTabCompleter(command);
        }

        Bukkit.getPluginManager().registerEvents(new PlayerListener(this, matchManager), this);
        Bukkit.getPluginManager().registerEvents(monsterGameplayController, this);
        Bukkit.getPluginManager().registerEvents(monsterAbilityController, this);
        Bukkit.getPluginManager().registerEvents(monsterSkillController, this);
        Bukkit.getPluginManager().registerEvents(hunterCombatController, this);
        getLogger().info("Project EVOLVE v0.7.0 enabled (Hunter firearms + Tracker harpoon).");
    }

    @Override
    public void onDisable() {
        if (monsterGameplayController != null) monsterGameplayController.clearAll();
        if (monsterAbilityController != null) monsterAbilityController.clearAll();
        if (hunterCombatController != null) hunterCombatController.shutdown();
        if (testMonsterController != null) testMonsterController.shutdown();
        if (matchManager != null) matchManager.shutdown();
    }
}
