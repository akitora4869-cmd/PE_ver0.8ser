package jp.evolvegame.core;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Goliath-inspired Monster ability kit. The gameplay is intentionally server authoritative;
 * the future custom client model only needs to provide matching animations/effects.
 */
public final class MonsterAbilityController implements Listener {
    private static final String ROCK_TAG = "project_evolve_monster_rock";

    private final ProjectEvolvePlugin plugin;
    private final MatchManager match;
    private final TestMonsterController monsterController;
    private final Map<UUID, Map<String, Long>> cooldownEnds = new HashMap<>();
    private final Set<UUID> activeLeap = new HashSet<>();
    private final Set<UUID> activeCharge = new HashSet<>();
    private final Set<UUID> activeBreath = new HashSet<>();

    public MonsterAbilityController(ProjectEvolvePlugin plugin, MatchManager match,
                                    TestMonsterController monsterController) {
        this.plugin = plugin;
        this.match = match;
        this.monsterController = monsterController;
    }

    public boolean use(Player monster, String id) {
        if (!isMonster(monster) || match.isMonsterDead()) return false;
        if (match.isMonsterEvolving()) {
            monster.sendActionBar(ChatColor.LIGHT_PURPLE + "進化中はスキルを使用できません。");
            return false;
        }
        if (isBusy(monster) && !"fire_breath".equals(id)) {
            monster.sendActionBar(ChatColor.RED + "別のスキル動作中です。");
            return false;
        }
        return switch (id) {
            case "fire_breath" -> fireBreath(monster);
            case "leap_smash" -> leapSmash(monster);
            case "charge" -> charge(monster);
            case "rock_throw" -> rockThrow(monster);
            default -> false;
        };
    }

    public boolean isBusy(Player player) {
        UUID id = player.getUniqueId();
        return activeLeap.contains(id) || activeCharge.contains(id) || activeBreath.contains(id);
    }

    private boolean fireBreath(Player monster) {
        if (!startCooldown(monster, "fire_breath", Material.FIRE_CHARGE)) return false;
        UUID id = monster.getUniqueId();
        activeBreath.add(id);
        int stage = match.getMonsterStage();
        String root = "monster.skills.fire-breath.stage-" + stage;
        double range = plugin.getConfig().getDouble(root + ".range", 7.0 + stage);
        double arc = plugin.getConfig().getDouble(root + ".arc-degrees", 58.0 + stage * 4.0);
        double damagePerPulse = plugin.getConfig().getDouble(root + ".damage-per-pulse", 1.5 + stage * 0.5);
        int pulses = Math.max(1, plugin.getConfig().getInt(root + ".pulses", 7));
        int interval = Math.max(1, plugin.getConfig().getInt("monster.skills.fire-breath.pulse-interval-ticks", 4));
        int fireTicks = Math.max(0, plugin.getConfig().getInt(root + ".fire-ticks", 50 + stage * 10));

        monster.getWorld().playSound(monster.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.65f, 1.35f);
        monster.sendActionBar(ChatColor.GOLD + "FIRE BREATH");

        final int[] pulse = {0};
        final BukkitTask[] holder = new BukkitTask[1];
        holder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isMonster(monster) || match.isMonsterEvolving() || pulse[0] >= pulses) {
                activeBreath.remove(id);
                holder[0].cancel();
                return;
            }
            doBreathPulse(monster, range, arc, damagePerPulse, fireTicks);
            pulse[0]++;
            if (pulse[0] >= pulses) {
                activeBreath.remove(id);
                holder[0].cancel();
            }
        }, 0L, interval);
        return true;
    }

    private void doBreathPulse(Player monster, double range, double arcDegrees, double damage, int fireTicks) {
        Location eye = monster.getEyeLocation();
        Vector forward = horizontalDirection(eye.getDirection());
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        World world = monster.getWorld();

        for (double d = 0.8; d <= range; d += 0.55) {
            double width = Math.tan(Math.toRadians(arcDegrees * 0.5)) * d;
            for (int s = -2; s <= 2; s++) {
                double side = width * (s / 2.0) * 0.78;
                Vector point = eye.toVector().add(forward.clone().multiply(d)).add(right.clone().multiply(side));
                point.setY(point.getY() - 0.22 + Math.random() * 0.55);
                world.spawnParticle(Particle.FLAME, point.toLocation(world), 1, 0.05, 0.05, 0.05, 0.015);
                if (Math.random() < 0.3) world.spawnParticle(Particle.SMOKE, point.toLocation(world), 1, 0.08, 0.08, 0.08, 0.01);
            }
        }

        for (LivingEntity target : targetsInCone(monster, range, arcDegrees, 4.0)) {
            target.damage(damage);
            target.setFireTicks(Math.max(target.getFireTicks(), fireTicks));
        }
        world.playSound(monster.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.65f, 0.72f);
    }

    private boolean leapSmash(Player monster) {
        if (!startCooldown(monster, "leap_smash", Material.RABBIT_FOOT)) return false;
        UUID id = monster.getUniqueId();
        activeLeap.add(id);
        int stage = match.getMonsterStage();
        String root = "monster.skills.leap-smash.stage-" + stage;
        double forwardPower = plugin.getConfig().getDouble(root + ".forward-power", 1.35 + stage * 0.12);
        double upwardPower = plugin.getConfig().getDouble(root + ".upward-power", 0.92 + stage * 0.06);
        double radius = plugin.getConfig().getDouble(root + ".radius", 4.0 + stage * 0.5);
        double damage = plugin.getConfig().getDouble(root + ".damage", 8.0 + stage * 3.0);

        Vector launch = horizontalDirection(monster.getEyeLocation().getDirection()).multiply(forwardPower).setY(upwardPower);
        monster.setVelocity(launch);
        monster.getWorld().playSound(monster.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.9f, 1.35f);
        monster.sendActionBar(ChatColor.YELLOW + "LEAP SMASH");

        long started = Bukkit.getCurrentTick();
        final BukkitTask[] holder = new BukkitTask[1];
        holder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isMonster(monster) || match.isMonsterEvolving()) {
                activeLeap.remove(id);
                holder[0].cancel();
                return;
            }
            long age = Bukkit.getCurrentTick() - started;
            if (age > 8 && monster.isOnGround()) {
                activeLeap.remove(id);
                holder[0].cancel();
                slam(monster, radius, damage);
            } else if (age > 80) {
                activeLeap.remove(id);
                holder[0].cancel();
            }
        }, 1L, 1L);
        return true;
    }

    private void slam(Player monster, double radius, double damage) {
        Location center = monster.getLocation();
        World world = monster.getWorld();
        world.spawnParticle(Particle.EXPLOSION, center.clone().add(0, 0.25, 0), 2, 0.5, 0.15, 0.5, 0.0);
        world.spawnParticle(Particle.BLOCK, center.clone().add(0, 0.15, 0), 90, radius * 0.45, 0.18, radius * 0.45, 0.18,
                center.getBlock().getBlockData());
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.15f, 0.72f);
        for (LivingEntity target : targetsInRadius(monster, center, radius, 3.2)) {
            target.damage(damage);
            Vector push = target.getLocation().toVector().subtract(center.toVector()).setY(0);
            if (push.lengthSquared() < 0.001) push = new Vector(0.1, 0, 0);
            target.setVelocity(target.getVelocity().add(push.normalize().multiply(0.9).setY(0.48)));
        }
    }

    private boolean charge(Player monster) {
        if (!startCooldown(monster, "charge", Material.GOAT_HORN)) return false;
        UUID id = monster.getUniqueId();
        activeCharge.add(id);
        int stage = match.getMonsterStage();
        String root = "monster.skills.charge.stage-" + stage;
        int duration = Math.max(5, plugin.getConfig().getInt(root + ".duration-ticks", 24 + stage * 2));
        double speed = plugin.getConfig().getDouble(root + ".speed", 1.18 + stage * 0.08);
        double hitRadius = plugin.getConfig().getDouble(root + ".hit-radius", 1.65 + stage * 0.1);
        double damage = plugin.getConfig().getDouble(root + ".damage", 7.0 + stage * 2.5);
        Set<UUID> hit = new HashSet<>();
        monster.sendActionBar(ChatColor.RED + "CHARGE");
        monster.getWorld().playSound(monster.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.0f, 0.85f);

        final int[] age = {0};
        final BukkitTask[] holder = new BukkitTask[1];
        holder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isMonster(monster) || match.isMonsterEvolving() || age[0]++ >= duration) {
                activeCharge.remove(id);
                holder[0].cancel();
                return;
            }
            Vector dir = horizontalDirection(monster.getEyeLocation().getDirection());
            monster.setVelocity(dir.clone().multiply(speed).setY(Math.max(-0.08, monster.getVelocity().getY())));
            monster.getWorld().spawnParticle(Particle.CLOUD, monster.getLocation().clone().add(0, 0.25, 0), 5, 0.5, 0.15, 0.5, 0.01);
            for (LivingEntity target : targetsInRadius(monster, monster.getLocation().clone().add(dir.clone().multiply(0.9)), hitRadius, 3.0)) {
                if (!hit.add(target.getUniqueId())) continue;
                target.damage(damage);
                target.setVelocity(dir.clone().multiply(1.35).setY(0.34));
                monster.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.9f, 0.62f);
            }
        }, 0L, 1L);
        return true;
    }

    private boolean rockThrow(Player monster) {
        if (!startCooldown(monster, "rock_throw", Material.COBBLESTONE)) return false;
        int stage = match.getMonsterStage();
        String root = "monster.skills.rock-throw.stage-" + stage;
        double velocity = plugin.getConfig().getDouble(root + ".velocity", 1.65 + stage * 0.08);

        Snowball rock = monster.launchProjectile(Snowball.class);
        rock.addScoreboardTag(ROCK_TAG);
        rock.setItem(new ItemStack(Material.COBBLESTONE));
        rock.setVelocity(monster.getEyeLocation().getDirection().normalize().multiply(velocity));
        monster.getWorld().playSound(monster.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 0.8f, 0.65f);
        monster.sendActionBar(ChatColor.GRAY + "ROCK THROW");
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onRockHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball rock) || !rock.getScoreboardTags().contains(ROCK_TAG)) return;
        if (!(rock.getShooter() instanceof Player monster) || !isMonster(monster)) {
            rock.remove();
            return;
        }
        int stage = match.getMonsterStage();
        String root = "monster.skills.rock-throw.stage-" + stage;
        double radius = plugin.getConfig().getDouble(root + ".radius", 3.1 + stage * 0.35);
        double damage = plugin.getConfig().getDouble(root + ".damage", 10.0 + stage * 3.0);
        Location impact = rock.getLocation();
        World world = impact.getWorld();
        world.spawnParticle(Particle.EXPLOSION, impact, 2, 0.25, 0.25, 0.25, 0.0);
        world.spawnParticle(Particle.BLOCK, impact, 65, radius * 0.35, radius * 0.25, radius * 0.35, 0.18,
                Material.STONE.createBlockData());
        world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
        for (LivingEntity target : targetsInRadius(monster, impact, radius, radius)) {
            target.damage(damage);
            Vector push = target.getLocation().toVector().subtract(impact.toVector());
            if (push.lengthSquared() < 0.001) push = new Vector(0.1, 0, 0);
            target.setVelocity(target.getVelocity().add(push.normalize().multiply(0.95).setY(0.34)));
        }
        rock.remove();
    }

    private boolean startCooldown(Player monster, String skill, Material material) {
        int stage = match.getMonsterStage();
        String root = switch (skill) {
            case "fire_breath" -> "fire-breath";
            case "leap_smash" -> "leap-smash";
            case "rock_throw" -> "rock-throw";
            default -> skill;
        };
        int seconds = Math.max(0, plugin.getConfig().getInt("monster.skills." + root + ".cooldown-seconds", switch (skill) {
            case "fire_breath" -> 10;
            case "leap_smash" -> 12;
            case "charge" -> 11;
            case "rock_throw" -> 9;
            default -> 10;
        }));
        long now = Bukkit.getCurrentTick();
        Map<String, Long> playerCd = cooldownEnds.computeIfAbsent(monster.getUniqueId(), k -> new HashMap<>());
        long end = playerCd.getOrDefault(skill, 0L);
        if (end > now) {
            double remain = (end - now) / 20.0;
            monster.sendActionBar(ChatColor.GRAY + "クールダウン: " + String.format(Locale.ROOT, "%.1f", remain) + "s");
            return false;
        }
        long ticks = seconds * 20L;
        playerCd.put(skill, now + ticks);
        monster.setCooldown(material, (int) ticks);
        return true;
    }

    private List<LivingEntity> targetsInCone(Player monster, double range, double arcDegrees, double height) {
        Location eye = monster.getEyeLocation();
        Vector origin = eye.toVector();
        Vector forward = horizontalDirection(eye.getDirection());
        double halfArc = Math.toRadians(arcDegrees * 0.5);
        List<LivingEntity> out = new ArrayList<>();
        for (Entity entity : monster.getWorld().getNearbyEntities(monster.getLocation(), range + 1.5, height, range + 1.5)) {
            if (!(entity instanceof LivingEntity target) || !isValidTarget(monster, target)) continue;
            BoundingBox box = entity.getBoundingBox();
            Vector center = box.getCenter();
            Vector delta = center.clone().subtract(origin);
            if (Math.abs(delta.getY()) > height) continue;
            Vector horizontal = delta.clone().setY(0);
            double distance = horizontal.length();
            if (distance > range || distance < 0.001) continue;
            horizontal.normalize();
            double dot = Math.max(-1, Math.min(1, horizontal.dot(forward)));
            if (Math.acos(dot) <= halfArc) out.add(target);
        }
        return out;
    }

    private List<LivingEntity> targetsInRadius(Player monster, Location center, double radius, double yRadius) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, yRadius, radius)) {
            if (entity instanceof LivingEntity target && isValidTarget(monster, target)
                    && target.getLocation().distanceSquared(center) <= radius * radius + yRadius * yRadius) {
                out.add(target);
            }
        }
        return out;
    }

    private boolean isValidTarget(Player monster, LivingEntity target) {
        if (target.getUniqueId().equals(monster.getUniqueId())) return false;
        LivingEntity body = monsterController.getBody(monster.getUniqueId());
        if (body != null && target.getUniqueId().equals(body.getUniqueId())) return false;
        if (target instanceof Player player) {
            return match.getJoined().contains(player.getUniqueId()) && match.getRole(player.getUniqueId()) != Role.MONSTER;
        }
        return target.getScoreboardTags().contains("project_evolve_wildlife");
    }

    private boolean isMonster(Player player) {
        return player != null && monsterController.isControlled(player)
                && player.getUniqueId().equals(match.getMonsterId());
    }

    private Vector horizontalDirection(Vector source) {
        Vector v = source.clone().setY(0);
        if (v.lengthSquared() < 0.0001) v = new Vector(0, 0, 1);
        return v.normalize();
    }

    public void clearAll() {
        cooldownEnds.clear();
        activeLeap.clear();
        activeCharge.clear();
        activeBreath.clear();
    }
}
