package thunder.hack.features.modules.combat;

import baritone.api.BaritoneAPI;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ShulkerBulletEntity;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import org.jetbrains.annotations.NotNull;
import thunder.hack.ThunderHack;
import thunder.hack.core.Core;
import thunder.hack.core.Managers;
import thunder.hack.core.manager.client.ModuleManager;
import thunder.hack.events.impl.EventSync;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.events.impl.PlayerUpdateEvent;
import thunder.hack.gui.notification.Notification;
import thunder.hack.injection.accesors.ILivingEntity;
import thunder.hack.features.modules.Module;
import thunder.hack.features.modules.client.HudEditor;
import thunder.hack.features.modules.movement.AutoSprint;
import thunder.hack.setting.Setting;
import thunder.hack.setting.impl.BooleanSettingGroup;
import thunder.hack.setting.impl.SettingGroup;
import thunder.hack.utility.Timer;
import thunder.hack.utility.interfaces.IOtherClientPlayerEntity;
import thunder.hack.utility.math.MathUtility;
import thunder.hack.utility.player.InteractionUtility;
import thunder.hack.utility.player.InventoryUtility;
import thunder.hack.utility.player.PlayerUtility;
import thunder.hack.utility.player.SearchInvResult;
import thunder.hack.utility.render.Render2DEngine;
import thunder.hack.utility.render.Render3DEngine;
import thunder.hack.utility.render.animation.CaptureMark;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import static net.minecraft.util.UseAction.BLOCK;
import static net.minecraft.util.math.MathHelper.wrapDegrees;
import static thunder.hack.features.modules.client.ClientSettings.isRu;
import static thunder.hack.utility.math.MathUtility.random;


public class Aura extends Module {
	public final Setting<Float> attackRange = new Setting<>("Range", 3.1f, 1f, 6.0f);
	public final Setting<Float> wallRange = new Setting<>("WallsRange", 3.1f, 0f, 6.0f);
	public final Setting<Float> aimRange = new Setting<>("AimRange", 2.0f, 0f, 6.0f);
	public final Setting<Integer> fov = new Setting<>("FOV", 180, 1, 180);
	public final Setting<Boolean> elytra = new Setting<>("ElytraOverride", false);
	public final Setting<Float> elytraAttackRange = new Setting<>("ElytraRange", 3.1f, 1f, 6.0f, v -> elytra.getValue());
	public final Setting<Float> elytraWallRange = new Setting<>("ElytraThroughWallsRange", 3.1f, 0f, 6.0f, v -> elytra.getValue());
	public final Setting<WallsBypass> wallsBypass = new Setting<>("WallsBypass", WallsBypass.Off, v -> getWallRange() > 0);
	public final Setting<Switch> switchMode = new Setting<>("AutoWeapon", Switch.None);
	public final Setting<Boolean> onlyWeapon = new Setting<>("OnlyWeapon", false, v -> switchMode.getValue() != Switch.Silent);

	/* Attack Sét ting */
	public final Setting<SettingGroup> AttackSettings = new Setting<>("Attack Settings", new SettingGroup(false, 0));
	public final Setting<Boolean> smartCrit = new Setting<>("Smart Crit", false).addToGroup(AttackSettings);
	public final Setting<Boolean> onlySpace = new Setting<>("OnlyCrit", false).addToGroup(AttackSettings);
	public final Setting<Boolean> autoJump = new Setting<>("AutoJump", false).addToGroup(AttackSettings);
	public final Setting<Boolean> shieldBreaker = new Setting<>("ShieldBreaker", true).addToGroup(AttackSettings);
	public final Setting<Boolean> unpressShield = new Setting<>("UnpressShield", true).addToGroup(AttackSettings);
	public final Setting<Float> attackCooldown = new Setting<>("AttackCooldown", 0.9f, 0.5f, 1f).addToGroup(AttackSettings);
	public final Setting<Float> attackBaseTime = new Setting<>("AttackBaseTime", 0.5f, 0f, 2f).addToGroup(AttackSettings);
	public final Setting<Integer> attackTickLimit = new Setting<>("AttackTickLimit", 11, 0, 20).addToGroup(AttackSettings);
	public final Setting<Float> critFallDistance = new Setting<>("CritFallDistance", 0f, 0f, 1f).addToGroup(AttackSettings);
	public final Setting<AttackHand> attackHand = new Setting<>("AttackHand", AttackHand.MainHand).addToGroup(AttackSettings);
	public final Setting<Boolean> randomHitDelay = new Setting<>("RandomHitDelay", false).addToGroup(AttackSettings);
	public final Setting<Boolean> tpsSync = new Setting<>("TPSSync", false).addToGroup(AttackSettings);
	public final Setting<Boolean> pullDown = new Setting<>("FastFall", false).addToGroup(AttackSettings);
	public final Setting<Boolean> onlyJumpBoost = new Setting<>("OnlyJumpBoost", false, v -> pullDown.getValue()).addToGroup(AttackSettings);
	public final Setting<Float> pullValue = new Setting<>("PullValue", 3f, 0f, 20f, v -> pullDown.getValue()).addToGroup(AttackSettings);
	public final Setting<BooleanSettingGroup> oldDelay = new Setting<>("OldDelay", new BooleanSettingGroup(false)).addToGroup(AttackSettings);
	public final Setting<Integer> minCPS = new Setting<>("MinCPS", 7, 1, 20).addToGroup(oldDelay);
	public final Setting<Integer> maxCPS = new Setting<>("MaxCPS", 12, 1, 20).addToGroup(oldDelay);

	/* Rotation Setting */
	public final Setting<SettingGroup> RotationSettings = new Setting<>("Rotation Settings", new SettingGroup(false, 0));
	public final Setting<Mode> rotationMode = new Setting<>("RotationMode", Mode.Track).addToGroup(RotationSettings);
	public final Setting<Integer> interactTicks = new Setting<>("InteractTicks", 3, 1, 10, v -> rotationMode.getValue() == Mode.Interact).addToGroup(RotationSettings);
	public final Setting<AccelerateOnHit> accelerateOnHit = new Setting<>("AccelerateOnHit", AccelerateOnHit.Off).addToGroup(RotationSettings);
	public final Setting<Integer> minYawStep = new Setting<>("MinYawStep", 65, 1, 180).addToGroup(RotationSettings);
	public final Setting<Integer> maxYawStep = new Setting<>("MaxYawStep", 75, 1, 180).addToGroup(RotationSettings);
	public final Setting<Float> aimedPitchStep = new Setting<>("AimedPitchStep", 1f, 0f, 90f).addToGroup(RotationSettings);
	public final Setting<Float> maxPitchStep = new Setting<>("MaxPitchStep", 8f, 1f, 90f).addToGroup(RotationSettings);
	public final Setting<Float> pitchAccelerate = new Setting<>("PitchAccelerate", 1.65f, 1f, 10f).addToGroup(RotationSettings);
	public final Setting<Boolean> clientLook = new Setting<>("ClientLook", false).addToGroup(RotationSettings);
	public final Setting<Boolean> lockTarget = new Setting<>("LockTarget", true).addToGroup(RotationSettings);
	public final Setting<Boolean> elytraTarget = new Setting<>("ElytraTarget", true).addToGroup(RotationSettings);
	public final Setting<RayTrace> rayTrace = new Setting<>("RayTrace", RayTrace.OnlyTarget).addToGroup(RotationSettings);
	public final Setting<Boolean> grimRayTrace = new Setting<>("GrimRayTrace", true).addToGroup(RotationSettings);
	public final Setting<Resolver> resolver = new Setting<>("Resolver", Resolver.Advantage).addToGroup(RotationSettings);
	public final Setting<Integer> backTicks = new Setting<>("BackTicks", 4, 1, 20, v -> resolver.is(Resolver.BackTrack)).addToGroup(RotationSettings);
	public final Setting<Boolean> resolverVisualisation = new Setting<>("ResolverVisualisation", false, v -> !resolver.is(Resolver.Off)).addToGroup(RotationSettings);

	/* Paues Setting */
	public final Setting<SettingGroup> PauseSettings = new Setting<>("Pause Settings", new SettingGroup(false, 0));
	public final Setting<Boolean> pauseWhileEating = new Setting<>("PauseWhileEating", false).addToGroup(PauseSettings);
	public final Setting<Boolean> pauseInInventory = new Setting<>("PauseInInventory", true).addToGroup(PauseSettings);
	public final Setting<Boolean> pauseBaritone = new Setting<>("PauseBaritone", false).addToGroup(PauseSettings);
	public final Setting<Boolean> deathDisable = new Setting<>("DisableOnDeath", true).addToGroup(PauseSettings);
	public final Setting<Boolean> tpDisable = new Setting<>("TPDisable", false).addToGroup(PauseSettings);

	/* more */
	public final Setting<Aura.SprintMode> sprintMode = new Setting<>("SprintMode", Aura.SprintMode.HVH);
	public final Setting<ESP> esp = new Setting<>("ESP", ESP.NextGen);
	public final Setting<SettingGroup> espGroup = new Setting<>("ESPSettings", new SettingGroup(false, 0), v -> esp.getValue() == ESP.NextGen || esp.getValue() == ESP.Dimasik
			|| esp.getValue() == ESP.NextGen);
	public final Setting<Integer> espLength = new Setting<>("NextGenLength", 14, 1, 100, v -> esp.is(ESP.NextGen)).addToGroup(espGroup);
	public final Setting<Float> espScale = new Setting<>("NextGenScale", 0.27F, 0.1F, 0.5F, v -> this.esp.is(Aura.ESP.NextGen)).addToGroup(this.espGroup);
	public final Setting<Integer> espLengthDimasik = new Setting<>("DimasikLength", 14, 1, 100, v -> esp.is(ESP.Dimasik)).addToGroup(espGroup);
	public final Setting<Float> espScaleDimasik = new Setting<>("DimasikScale", 0.27F, 0.1F, 0.5F, v -> esp.is(ESP.Dimasik)).addToGroup(espGroup);
	public final Setting<Sort> sort = new Setting<>("Sort", Sort.LowestDistance);

	/*   TARGETS   */
	public final Setting<SettingGroup> targets = new Setting<>("Targets", new SettingGroup(false, 0));
	public final Setting<Boolean> Players = new Setting<>("Players", true).addToGroup(targets);
	public final Setting<Boolean> Mobs = new Setting<>("Mobs", true).addToGroup(targets);
	public final Setting<Boolean> Animals = new Setting<>("Animals", true).addToGroup(targets);
	public final Setting<Boolean> Villagers = new Setting<>("Villagers", true).addToGroup(targets);
	public final Setting<Boolean> Slimes = new Setting<>("Slimes", true).addToGroup(targets);
	public final Setting<Boolean> hostiles = new Setting<>("Hostiles", true).addToGroup(targets);
	public final Setting<Boolean> onlyAngry = new Setting<>("OnlyAngryHostiles", true, v -> hostiles.getValue()).addToGroup(targets);
	public final Setting<Boolean> Projectiles = new Setting<>("Projectiles", true).addToGroup(targets);
	public final Setting<Boolean> ignoreInvisible = new Setting<>("IgnoreInvisibleEntities", false).addToGroup(targets);
	public final Setting<Boolean> ignoreNamed = new Setting<>("IgnoreNamed", false).addToGroup(targets);
	public final Setting<Boolean> ignoreTeam = new Setting<>("IgnoreTeam", false).addToGroup(targets);
	public final Setting<Boolean> ignoreCreative = new Setting<>("IgnoreCreative", true).addToGroup(targets);
	public final Setting<Boolean> ignoreNaked = new Setting<>("IgnoreNaked", false).addToGroup(targets);
	public final Setting<Boolean> ignoreShield = new Setting<>("AttackShieldingEntities", true).addToGroup(targets);
	public static Entity target;
	public float rotationYaw;
	public float rotationPitch;
	public float pitchAcceleration = 1f;
	private Vec3d rotationPoint = Vec3d.ZERO;
	private Vec3d rotationMotion = Vec3d.ZERO;
	private int hitTicks;
	private int trackticks;
	private boolean lookingAtHitbox;
	private final Timer delayTimer = new Timer();
	private final Timer pauseTimer = new Timer();
	private boolean isMovingReverse = false;
	private float prevForward = 0.0f;
	private float prevSideways = 0.0f;
	public Box resolvedBox;
	static boolean wasTargeted = false;
	private Vec3d aimOffset = Vec3d.ZERO;
	private int offsetTicks = 0;

	public Aura() {
		super("Aura", Category.COMBAT);
	}

	private float getattackRange() {
		return elytra.getValue() && mc.player.isFallFlying() ? elytraAttackRange.getValue() : attackRange.getValue();
	}
	private float getWallRange() {
		return elytra.getValue() && mc.player != null && mc.player.isFallFlying() ? elytraWallRange.getValue() : wallRange.getValue();
	}

	public void auraLogic() {
		if (!haveWeapon()) {
			target = null;
			return;
		}

		handleKill();
		updateTarget();

		if (target == null) {
			return;
		}

		if (!mc.options.jumpKey.isPressed() && mc.player.isOnGround() && autoJump.getValue())
			mc.player.jump();

		boolean readyForAttack;
		if (this.grimRayTrace.getValue()) {
			readyForAttack = this.autoCrit() && (this.lookingAtHitbox || this.skipRayTraceCheck());
			this.calcRotations(this.autoCrit());
		} else {
			this.calcRotations(this.autoCrit());
			readyForAttack = this.autoCrit() && (this.lookingAtHitbox || this.skipRayTraceCheck());
		}

		if (readyForAttack) {
			if (shieldBreaker(false))
				return;

			boolean[] playerState = preAttack();
			if (!(target instanceof PlayerEntity pl) || !(pl.isUsingItem() && pl.getOffHandStack().getItem() == Items.SHIELD) || ignoreShield.getValue())
				attack();

			postAttack(playerState[0], playerState[1]);
		}
	}

	private boolean haveWeapon() {
		Item handItem = mc.player.getMainHandStack().getItem();
		if (onlyWeapon.getValue()) {
			if (switchMode.getValue() == Switch.None) {
				return handItem instanceof SwordItem || handItem instanceof AxeItem || handItem instanceof TridentItem;
			} else {
				return (InventoryUtility.getSwordHotBar().found() || InventoryUtility.getAxeHotBar().found());
			}
		}
		return true;
	}

	private boolean skipRayTraceCheck() {
		return rotationMode.getValue() == Mode.None || rayTrace.getValue() == RayTrace.OFF
			   || rotationMode.is(Mode.Grim)
			   || (rotationMode.is(Mode.Interact) && (interactTicks.getValue() <= 1
					   || mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().expand(-0.25, 0.0, -0.25).offset(0.0, 1, 0.0)).iterator().hasNext()));
	}

	public void attack() {
    Criticals.cancelCrit = true;
    ModuleManager.criticals.doCrit();

    boolean usedMaceSwap = ModuleManager.maceSwap != null && ModuleManager.maceSwap.isEnabled();

    if (usedMaceSwap && target instanceof LivingEntity) {
        if (ModuleManager.maceSwap.silentSwap((LivingEntity) target) != -1 
            || ModuleManager.maceSwap.isCurrentlySwitching()) {
            mc.interactionManager.attackEntity(mc.player, target);
            Criticals.cancelCrit = false;
            swingHand();
            hitTicks = getHitTicks();
            return;
        }
    }

    // Nếu không dùng MaceSwap hoặc target không phải LivingEntity → xử lý bình thường
    int previousSlot = this.switchMethod();

    mc.interactionManager.attackEntity(mc.player, target);
    Criticals.cancelCrit = false;
    swingHand();
    hitTicks = getHitTicks();

    if (previousSlot != -1) {
        InventoryUtility.switchTo(previousSlot);
       }
   }

	private boolean @NotNull [] preAttack() {
		boolean blocking = mc.player.isUsingItem() && mc.player.getActiveItem().getItem().getUseAction(mc.player.getActiveItem()) == BLOCK;
		if (blocking && unpressShield.getValue())
			sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN));

		boolean sprint = Core.serverSprint;

// --- Pre-action logic ---
		if (this.sprintMode.getValue() == Aura.SprintMode.HVH && sprint) {
			this.disableSprint();
		} else if (this.sprintMode.getValue() == Aura.SprintMode.Legit && sprint) {
			this.prepareLegitSprint(); // thay thế disableAutoSprintAndReverse()
		}

		if (this.rotationMode.is(Aura.Mode.Grim)) {
			this.sendPacket(
				new PlayerMoveC2SPacket.Full(
					mc.player.getX(),
					mc.player.getY(),
					mc.player.getZ(),
					this.rotationYaw,
					this.rotationPitch,
					mc.player.isOnGround()
				)
			);
		}

		return new boolean[] {blocking, sprint};
	}

// --- Post-action logic ---
	public void postAttack(boolean block, boolean sprint) {
		if (this.sprintMode.getValue() == Aura.SprintMode.HVH && sprint) {
			this.enableSprint();
		} else if (this.sprintMode.getValue() == Aura.SprintMode.Legit && sprint) {
			this.restoreLegitSprint(); // thay thế enableAutoSprintAndReleaseReverse()
		}

		if (block && this.unpressShield.getValue()) {
			this.sendSequencedPacket(id ->
									 new PlayerInteractItemC2SPacket(
										 Hand.MAIN_HAND,
										 id,
										 this.rotationYaw,
										 this.rotationPitch
									 )
									);
		}

		if (this.rotationMode.is(Aura.Mode.Grim)) {
			this.sendPacket(
				new PlayerMoveC2SPacket.Full(
					mc.player.getX(),
					mc.player.getY(),
					mc.player.getZ(),
					mc.player.getYaw(),
					mc.player.getPitch(),
					mc.player.isOnGround()
				)
			);
		}
	}

// --- Legit sprint mode helpers ---
	private boolean wasSprinting = false;

	private void prepareLegitSprint() {
		if (mc.player == null) return;

		wasSprinting = mc.player.isSprinting();

		// tạm dừng autoSprint trong khoảnh khắc
		if (ModuleManager.autoSprint.isEnabled()) {
			AutoSprint.forceSprint = false;
		}

		mc.player.setSprinting(false); // tạm dừng sprint
		mc.options.sprintKey.setPressed(false);
	}

	private void restoreLegitSprint() {
		if (mc.player == null) return;

		mc.player.setSprinting(wasSprinting); // restore sprint tự nhiên
		mc.options.sprintKey.setPressed(wasSprinting);

		// restore autoSprint
		if (ModuleManager.autoSprint.isEnabled()) {
			AutoSprint.forceSprint = true;
		}
	}

// --- HVH sprint ---
	private void disableSprint() {
		mc.player.setSprinting(false);
		mc.options.sprintKey.setPressed(false);
		this.sendPacket(
			new ClientCommandC2SPacket(
				mc.player,
				ClientCommandC2SPacket.Mode.STOP_SPRINTING
			)
		);
	}

	private void enableSprint() {
		mc.player.setSprinting(true);
		mc.options.sprintKey.setPressed(true);
		this.sendPacket(
			new ClientCommandC2SPacket(
				mc.player,
				ClientCommandC2SPacket.Mode.START_SPRINTING
			)
		);
	}

// --- AutoSprint helpers ---
	private void disableAutoSprint() {
		if (ModuleManager.autoSprint.isEnabled()) {
			AutoSprint.forceSprint = false;
		}
	}

	private void enableAutoSprint() {
		if (ModuleManager.autoSprint.isEnabled()) {
			AutoSprint.forceSprint = true;
		}
	}

// --- Resolver logic ---
	public void resolvePlayers() {
		if (this.resolver.not(Aura.Resolver.Off)) {
			for (PlayerEntity player : mc.world.getPlayers()) {
				if (player instanceof OtherClientPlayerEntity) {
					((IOtherClientPlayerEntity) player)
					.resolve(this.resolver.getValue());
				}
			}
		}
	}

	public void restorePlayers() {
		if (this.resolver.not(Aura.Resolver.Off)) {
			for (PlayerEntity player : mc.world.getPlayers()) {
				if (player instanceof OtherClientPlayerEntity) {
					((IOtherClientPlayerEntity) player)
					.releaseResolver();
				}
			}
		}
	}
	public void handleKill() {
		if (target instanceof LivingEntity && (((LivingEntity) target).getHealth() <= 0 || ((LivingEntity) target).isDead()))
			Managers.NOTIFICATION.publicity("Aura", isRu() ? "Цель успешно нейтрализована!" : "Target successfully neutralized!", 3, Notification.Type.SUCCESS);
	}

	private int switchMethod() {
		int prevSlot = -1;
		SearchInvResult swordResult = InventoryUtility.getSwordHotBar();
		if (swordResult.found() && switchMode.getValue() != Switch.None) {
			if (switchMode.getValue() == Switch.Silent)
				prevSlot = mc.player.getInventory().selectedSlot;
			swordResult.switchTo();
		}

		return prevSlot;
	}

	private int getHitTicks() {
		return oldDelay.getValue().isEnabled() ? 1 + (int)(20f / random(minCPS.getValue(), maxCPS.getValue())) : (shouldRandomizeDelay() ? (int) MathUtility.random(11, 13) : attackTickLimit.getValue());
	}

	@EventHandler
	public void onUpdate(PlayerUpdateEvent e) {
		if (!pauseTimer.passedMs(1000))
			return;

		if (mc.player.isUsingItem() && pauseWhileEating.getValue())
			return;
		if (pauseBaritone.getValue() && ThunderHack.baritone) {
			boolean isTargeted = (target != null);
			if (isTargeted && !wasTargeted) {
				BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("pause");
				wasTargeted = true;
			} else if (!isTargeted && wasTargeted) {
				BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("resume");
				wasTargeted = false;
			}
		}

		resolvePlayers();
		auraLogic();
		restorePlayers();
		hitTicks--;
	}

	@EventHandler
	public void onSync(EventSync e) {
		if (!pauseTimer.passedMs(1000))
			return;

		if (mc.player.isUsingItem() && pauseWhileEating.getValue())
			return;

		if (!haveWeapon())
			return;

		if (target != null && rotationMode.getValue() != Mode.None && rotationMode.getValue() != Mode.Grim) {
			mc.player.setYaw(rotationYaw);
			mc.player.setPitch(rotationPitch);
		} else {
			rotationYaw = mc.player.getYaw();
			rotationPitch = mc.player.getPitch();
		}

		if (oldDelay.getValue().isEnabled())
			if (minCPS.getValue() > maxCPS.getValue())
				minCPS.setValue(maxCPS.getValue());

		if (target != null && pullDown.getValue() && (mc.player.hasStatusEffect(StatusEffects.JUMP_BOOST) || !onlyJumpBoost.getValue()))
			mc.player.addVelocity(0f, -pullValue.getValue() / 1000f, 0f);
	}

	@EventHandler
	public void onPacketSend(PacketEvent.@NotNull Send e) {
		if (e.getPacket() instanceof PlayerInteractEntityC2SPacket pie && Criticals.getInteractType(pie) != Criticals.InteractType.ATTACK && target != null)
			e.cancel();
	}

	@EventHandler
	public void onPacketReceive(PacketEvent.@NotNull Receive e) {
		if (e.getPacket() instanceof EntityStatusS2CPacket status)
			if (status.getStatus() == 30 && status.getEntity(mc.world) != null && target != null && status.getEntity(mc.world) == target)
				Managers.NOTIFICATION.publicity("Aura", isRu() ? ("Успешно сломали щит игроку " + target.getName().getString()) : ("Succesfully destroyed " + target.getName().getString() +
												"'s shield"), 2, Notification.Type.SUCCESS);

		if (e.getPacket() instanceof PlayerPositionLookS2CPacket && tpDisable.getValue())
			disable(isRu() ? "Отключаю из-за телепортации!" : "Disabling due to tp!");

		if (e.getPacket() instanceof EntityStatusS2CPacket pac && pac.getStatus() == 3 && pac.getEntity(mc.world) == mc.player && deathDisable.getValue())
			disable(isRu() ? "Отключаю из-за смерти!" : "Disabling due to death!");

		/*
		if (resolver.is(Resolver.BackTrack) && e.getPacket() instanceof CommonPingS2CPacket ping && target != null) {
		    Managers.ASYNC.run(() -> mc.executeSync(() -> ping.apply(mc.getNetworkHandler())), backTicks.getValue() * 25L);
		    e.cancel();
		}*/
	}

	@Override
	public void onEnable() {
		target = null;
		lookingAtHitbox = false;
		rotationPoint = Vec3d.ZERO;
		rotationMotion = Vec3d.ZERO;
		rotationYaw = mc.player.getYaw();
		rotationPitch = mc.player.getPitch();
		delayTimer.reset();
	}

	private boolean autoCrit() {
    boolean reasonForSkipCrit =
        !smartCrit.getValue()
        || mc.player.getAbilities().flying
        || (mc.player.isFallFlying() || ModuleManager.elytraPlus.isEnabled())
        || mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
        || mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING)
        || Managers.PLAYER.isInWeb();

    if (hitTicks > 0) return false;
    if (pauseInInventory.getValue() && Managers.PLAYER.inInventory) return false;
    if (getAttackCooldown() < attackCooldown.getValue() && !oldDelay.getValue().isEnabled()) return false;

    if (ModuleManager.criticals.isEnabled() && ModuleManager.criticals.mode.is(Criticals.Mode.Grim)) return true;

    // Các trường hợp đặc biệt luôn crit (lava/water/above water)
    if (mc.player.isInLava() || mc.player.isSubmergedInWater()) return true;
    if (!mc.options.jumpKey.isPressed() && isAboveWater()) return true;

    // Merge conditions để tránh jump không cần thiết
    boolean mergeWithTargetStrafe = !ModuleManager.targetStrafe.isEnabled() || !ModuleManager.targetStrafe.jump.getValue();
    boolean mergeWithSpeed = !ModuleManager.speed.isEnabled() || mc.player.isOnGround();

    // Chỉ crit khi falling đúng cách (strict hơn)
    if (!reasonForSkipCrit 
        && !mc.player.isOnGround() 
        && mc.player.fallDistance > (shouldRandomizeFallDistance() ? MathUtility.random(0.1f, 0.5f) : critFallDistance.getValue() /* gợi ý giảm xuống 0.1-0.3 để dễ crit hơn */)
        && (!mc.options.jumpKey.isPressed() || onlySpace.getValue() || autoJump.getValue() || !mergeWithTargetStrafe || !mergeWithSpeed)) {
        return true;
    }

    // Nếu không thỏa crit -> KHÔNG attack (tối ưu damage)
    return false;
   }

	private boolean shieldBreaker(boolean instant) { //todo - Actual value of parameter 'instant' is always 'false'
		int axeSlot = InventoryUtility.getAxe().slot();
		if (axeSlot == -1) return false;
		if (!shieldBreaker.getValue()) return false;
		if (!(target instanceof PlayerEntity)) return false;
		if (!((PlayerEntity) target).isUsingItem() && !instant) return false;
		if (((PlayerEntity) target).getOffHandStack().getItem() != Items.SHIELD && ((PlayerEntity) target).getMainHandStack().getItem() != Items.SHIELD)
			return false;

		if (axeSlot >= 9) {
			mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, axeSlot, mc.player.getInventory().selectedSlot, SlotActionType.SWAP, mc.player);
			sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
			mc.interactionManager.attackEntity(mc.player, target);
			swingHand();
			mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, axeSlot, mc.player.getInventory().selectedSlot, SlotActionType.SWAP, mc.player);
			sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
		} else {
			sendPacket(new UpdateSelectedSlotC2SPacket(axeSlot));
			mc.interactionManager.attackEntity(mc.player, target);
			swingHand();
			sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot));
		}
		hitTicks = 10;
		return true;
	}

	private void swingHand() {
		switch (attackHand.getValue()) {
		case OffHand -> mc.player.swingHand(Hand.OFF_HAND);
		case MainHand -> mc.player.swingHand(Hand.MAIN_HAND);
		}
	}

	public boolean isAboveWater() {
		return mc.player.isSubmergedInWater() || mc.world.getBlockState(BlockPos.ofFloored(mc.player.getPos().add(0, -0.4, 0))).getBlock() == Blocks.WATER;
	}

	public float getAttackCooldownProgressPerTick() {
		return (float)(1.0 / mc.player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED) * (20.0 * ThunderHack.TICK_TIMER * (tpsSync.getValue() ? Managers.SERVER.getTPSFactor() : 1f)));
	}

	public float getAttackCooldown() {
		return MathHelper.clamp(((float)((ILivingEntity) mc.player).getLastAttackedTicks() + attackBaseTime.getValue()) / getAttackCooldownProgressPerTick(), 0.0F, 1.0F);
	}

	private void updateTarget() {
		Entity candidat = findTarget();

		if (target == null) {
			target = candidat;
			return;
		}

		if (sort.getValue() == Sort.FOV || !lockTarget.getValue())
			target = candidat;

		if (candidat instanceof ProjectileEntity)
			target = candidat;

		if (skipEntity(target))
			target = null;
	}

	private void calcRotations(boolean ready) {
		if (ready) {
			trackticks = (mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().expand(-0.25, 0.0, -0.25).offset(0.0, 1, 0.0)).iterator().hasNext() ? 1 : interactTicks.getValue());
		} else if (trackticks > 0) {
			trackticks--;
		}

		if (target == null)
			return;


		Vec3d targetVec;

		if (mc.player.isFallFlying() || ModuleManager.elytraPlus.isEnabled()) targetVec = target.getEyePos();
		else targetVec = getLegitLook(target);

		if (targetVec == null)
			return;

		pitchAcceleration = Managers.PLAYER.checkRtx(rotationYaw, rotationPitch, attackRange.getValue() + aimRange.getValue(), attackRange.getValue() + aimRange.getValue(), rayTrace.getValue())
							? aimedPitchStep.getValue() : pitchAcceleration < maxPitchStep.getValue() ? pitchAcceleration * pitchAccelerate.getValue() : maxPitchStep.getValue();

		float delta_yaw = wrapDegrees((float) wrapDegrees(Math.toDegrees(Math.atan2(targetVec.z - mc.player.getZ(), (targetVec.x - mc.player.getX()))) - 90) - rotationYaw) + (wallsBypass.is(WallsBypass.V2)
						  && !ready && !mc.player.canSee(target) ? 20 : 0);
		float delta_pitch = ((float)(-Math.toDegrees(Math.atan2(targetVec.y - (mc.player.getPos().y + mc.player.getEyeHeight(mc.player.getPose())), Math.sqrt(Math.pow((targetVec.x - mc.player.getX()),
									 2) + Math.pow(targetVec.z - mc.player.getZ(), 2))))) - rotationPitch);

		float yawStep = rotationMode.getValue() != Mode.Track ? 360f : random(minYawStep.getValue(), maxYawStep.getValue());
		float pitchStep = rotationMode.getValue() != Mode.Track ? 180f : Managers.PLAYER.ticksElytraFlying > 5 ? 180 : (pitchAcceleration + random(-1f, 1f));

		if (ready)
			switch (accelerateOnHit.getValue()) {
			case Yaw -> yawStep = 180f;
			case Pitch -> pitchStep = 90f;
			case Both -> {
					yawStep = 180f;
					pitchStep = 90f;
				}
			}

		if (delta_yaw > 180)
			delta_yaw = delta_yaw - 180;

		float deltaYaw = MathHelper.clamp(MathHelper.abs(delta_yaw), -yawStep, yawStep);

		float deltaPitch = MathHelper.clamp(delta_pitch, -pitchStep, pitchStep);

		float newYaw = rotationYaw + (delta_yaw > 0 ? deltaYaw : -deltaYaw);
		float newPitch = MathHelper.clamp(rotationPitch + deltaPitch, -90.0F, 90.0F);

		double gcdFix = (Math.pow(mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2, 3.0)) * 1.2;

		if (trackticks > 0 || rotationMode.getValue() == Mode.Track) {
			rotationYaw = (float)(newYaw - (newYaw - rotationYaw) % gcdFix);
			rotationPitch = (float)(newPitch - (newPitch - rotationPitch) % gcdFix);
		} else {
			rotationYaw = mc.player.getYaw();
			rotationPitch = mc.player.getPitch();
		}

		if (!rotationMode.is(Mode.Grim))
			ModuleManager.rotations.fixRotation = rotationYaw;
		lookingAtHitbox = Managers.PLAYER.checkRtx(rotationYaw, rotationPitch, attackRange.getValue(), getWallRange(), rayTrace.getValue());
	}

	public void onRender3D(MatrixStack stack) {
		if (!haveWeapon() || target == null)
			return;

		if ((resolver.is(Resolver.BackTrack) || resolverVisualisation.getValue()) && resolvedBox != null)
			Render3DEngine.OUTLINE_QUEUE.add(new Render3DEngine.OutlineAction(resolvedBox, HudEditor.getColor(0), 1));

		switch (esp.getValue()) {
		case CelkaPasta -> Render3DEngine.drawOldTargetEsp(stack, target);
		case NurikZapen -> CaptureMark.render(target);
		case NextGen -> Render3DEngine.renderGhosts(espLength.getValue(), espScale.getValue(), target);
		case Dimasik -> Render3DEngine.renderGhostDimasik(espLengthDimasik.getValue(), espScaleDimasik.getValue(), target);
		case ThunderHack -> Render3DEngine.drawTargetEsp(stack, target);
		}

		if (clientLook.getValue() && rotationMode.getValue() != Mode.None) {
			mc.player.setYaw((float) Render2DEngine.interpolate(mc.player.prevYaw, rotationYaw, Render3DEngine.getTickDelta()));
			mc.player.setPitch((float) Render2DEngine.interpolate(mc.player.prevPitch, rotationPitch, Render3DEngine.getTickDelta()));
		}
	}

	@Override
	public void onDisable() {
		target = null;
	}

	public float getSquaredRotateDistance() {
		float dst = getattackRange();
		dst += aimRange.getValue();
		if ((mc.player.isFallFlying() || ModuleManager.elytraPlus.isEnabled()) && target != null) dst += 4f;
		if (ModuleManager.strafe.isEnabled()) dst += 4f;
		if (rotationMode.getValue() != Mode.Track || rayTrace.getValue() == RayTrace.OFF)
			dst = getattackRange();

		return dst * dst;
	}

	/*
	 * Эта хуеверть основанна на приципе "DVD Logo"
	 * У нас есть точка и "коробка" (хитбокс цели)
	 * Точка летает внутри коробки и отталкивается от стенок с рандомной скоростью и легким джиттером
	 * Также выбирает лучшую дистанцию для удара, то есть считает не от центра до центра, а от наших глаз до достигаемых точек хитбокса цели
	 * Со стороны не сильно заметно что ты играешь с киллкой, в отличие от аур семейства Wexside
	 */

	public Vec3d getLegitLook(Entity target) {

		float minMotionXZ = 0.003f;
		float maxMotionXZ = 0.03f;

		float minMotionY = 0.001f;
		float maxMotionY = 0.03f;

		double lenghtX = target.getBoundingBox().getLengthX();
		double lenghtY = target.getBoundingBox().getLengthY();
		double lenghtZ = target.getBoundingBox().getLengthZ();


		// Задаем начальную скорость точки
		if (rotationMotion.equals(Vec3d.ZERO))
			rotationMotion = new Vec3d(random(-0.05f, 0.05f), random(-0.05f, 0.05f), random(-0.05f, 0.05f));

		rotationPoint = rotationPoint.add(rotationMotion);

		// Сталкиваемся с хитбоксом по X
		if (rotationPoint.x >= (lenghtX - 0.05) / 2f)
			rotationMotion = new Vec3d(-random(minMotionXZ, maxMotionXZ), rotationMotion.getY(), rotationMotion.getZ());

		// Сталкиваемся с хитбоксом по Y
		if (rotationPoint.y >= lenghtY)
			rotationMotion = new Vec3d(rotationMotion.getX(), -random(minMotionY, maxMotionY), rotationMotion.getZ());

		// Сталкиваемся с хитбоксом по Z
		if (rotationPoint.z >= (lenghtZ - 0.05) / 2f)
			rotationMotion = new Vec3d(rotationMotion.getX(), rotationMotion.getY(), -random(minMotionXZ, maxMotionXZ));

		// Сталкиваемся с хитбоксом по -X
		if (rotationPoint.x <= -(lenghtX - 0.05) / 2f)
			rotationMotion = new Vec3d(random(minMotionXZ, 0.03f), rotationMotion.getY(), rotationMotion.getZ());

		// Сталкиваемся с хитбоксом по -Y
		if (rotationPoint.y <= 0.05)
			rotationMotion = new Vec3d(rotationMotion.getX(), random(minMotionY, maxMotionY), rotationMotion.getZ());

		// Сталкиваемся с хитбоксом по -Z
		if (rotationPoint.z <= -(lenghtZ - 0.05) / 2f)
			rotationMotion = new Vec3d(rotationMotion.getX(), rotationMotion.getY(), random(minMotionXZ, maxMotionXZ));

		// Добавляем джиттер
		rotationPoint.add(random(-0.03f, 0.03f), 0f, random(-0.03f, 0.03f));

		if (!mc.player.canSee(target)) {
			// Если мы используем обход ударов через стену V1 и наша цель за стеной, то целимся в верхушку хитбокса т.к. матриксу поебать
			if (Objects.requireNonNull(wallsBypass.getValue()) == WallsBypass.V1) {
				return target.getPos().add(random(-0.15, 0.15), lenghtY, random(-0.15, 0.15));
			}
		}

		float[] rotation;

		// Если мы перестали смотреть на цель
		if (!Managers.PLAYER.checkRtx(rotationYaw, rotationPitch, attackRange.getValue(), getWallRange(), rayTrace.getValue())) {
			float[] rotation1 = Managers.PLAYER.calcAngle(target.getPos().add(0, target.getEyeHeight(target.getPose()) / 2f, 0));

			// Проверяем видимость центра игрока
			if (PlayerUtility.squaredDistanceFromEyes(target.getPos().add(0, target.getEyeHeight(target.getPose()) / 2f, 0)) <= attackRange.getPow2Value()
					&& Managers.PLAYER.checkRtx(rotation1[0], rotation1[1], attackRange.getValue(), 0, rayTrace.getValue())) {
				// наводим на центр
				rotationPoint = new Vec3d(random(-0.1f, 0.1f), target.getEyeHeight(target.getPose()) / (random(1.8f, 2.5f)), random(-0.1f, 0.1f));
			} else {
				// Сканим хитбокс на видимую точку
				float halfBox = (float)(lenghtX / 2f);

				for (float x1 = -halfBox; x1 <= halfBox; x1 += 0.05f) {
					for (float z1 = -halfBox; z1 <= halfBox; z1 += 0.05f) {
						for (float y1 = 0.05f; y1 <= target.getBoundingBox().getLengthY(); y1 += 0.15f) {

							Vec3d v1 = new Vec3d(target.getX() + x1, target.getY() + y1, target.getZ() + z1);

							// Скипаем, если вне досягаемости
							if (PlayerUtility.squaredDistanceFromEyes(v1) > attackRange.getPow2Value()) continue;

							rotation = Managers.PLAYER.calcAngle(v1);
							if (Managers.PLAYER.checkRtx(rotation[0], rotation[1], attackRange.getValue(), 0, rayTrace.getValue())) {
								// Наводимся, если видим эту точку
								rotationPoint = new Vec3d(x1, y1, z1);
								break;
							}
						}
					}
				}
			}
		}
		return target.getPos().add(rotationPoint);
	}

	public boolean isInRange(Entity target) {

		if (PlayerUtility.squaredDistanceFromEyes(target.getPos().add(0, target.getEyeHeight(target.getPose()), 0)) > getSquaredRotateDistance() + 4) {
			return false;
		}

		float[] rotation;
		float halfBox = (float)(target.getBoundingBox().getLengthX() / 2f);

		// уменьшил частоту выборки
		for (float x1 = -halfBox; x1 <= halfBox; x1 += 0.15f) {
			for (float z1 = -halfBox; z1 <= halfBox; z1 += 0.15f) {
				for (float y1 = 0.05f; y1 <= target.getBoundingBox().getLengthY(); y1 += 0.25f) {
					if (PlayerUtility.squaredDistanceFromEyes(new Vec3d(target.getX() + x1, target.getY() + y1, target.getZ() + z1)) > getSquaredRotateDistance())
						continue;

					rotation = Managers.PLAYER.calcAngle(new Vec3d(target.getX() + x1, target.getY() + y1, target.getZ() + z1));
					if (Managers.PLAYER.checkRtx(rotation[0], rotation[1], (float) Math.sqrt(getSquaredRotateDistance()), getWallRange(), rayTrace.getValue())) {
						return true;
					}
				}
			}
		}
		return false;
	}

	public Entity findTarget() {
		List<LivingEntity> first_stage = new CopyOnWriteArrayList<>();
		for (Entity ent : mc.world.getEntities()) {
			if ((ent instanceof ShulkerBulletEntity || ent instanceof FireballEntity)
					&& ent.isAlive()
					&& isInRange(ent)
					&& Projectiles.getValue()) {
				return ent;
			}
			if (skipEntity(ent)) continue;
			if (!(ent instanceof LivingEntity)) continue;
			first_stage.add((LivingEntity) ent);
		}

		return switch (sort.getValue()) {
		case LowestDistance ->
				first_stage.stream().min(Comparator.comparing(e -> (mc.player.squaredDistanceTo(e.getPos())))).orElse(null);

		case HighestDistance ->
				first_stage.stream().max(Comparator.comparing(e -> (mc.player.squaredDistanceTo(e.getPos())))).orElse(null);

		case FOV -> first_stage.stream().min(Comparator.comparing(this::getFOVAngle)).orElse(null);

		case LowestHealth ->
				first_stage.stream().min(Comparator.comparing(e -> (e.getHealth() + e.getAbsorptionAmount()))).orElse(null);

		case HighestHealth ->
				first_stage.stream().max(Comparator.comparing(e -> (e.getHealth() + e.getAbsorptionAmount()))).orElse(null);

		case LowestDurability -> first_stage.stream().min(Comparator.comparing(e -> {
				float v = 0;
				for (ItemStack armor : e.getArmorItems())
					if (armor != null && !armor.getItem().equals(Items.AIR)) {
						v += ((armor.getMaxDamage() - armor.getDamage()) / (float) armor.getMaxDamage());
					}
				return v;
			}
																				  )).orElse(null);

		case HighestDurability -> first_stage.stream().max(Comparator.comparing(e -> {
				float v = 0;
				for (ItemStack armor : e.getArmorItems())
					if (armor != null && !armor.getItem().equals(Items.AIR)) {
						v += ((armor.getMaxDamage() - armor.getDamage()) / (float) armor.getMaxDamage());
					}
				return v;
			}
																				   )).orElse(null);
		};
	}

	private boolean skipEntity(Entity entity) {
		if (isBullet(entity)) return false;
		if (!(entity instanceof LivingEntity ent)) return true;
		if (ent.isDead() || !entity.isAlive()) return true;
		if (entity instanceof ArmorStandEntity) return true;
		if (entity instanceof CatEntity) return true;
		if (skipNotSelected(entity)) return true;
		if (!InteractionUtility.isVecInFOV(ent.getPos(), fov.getValue())) return true;

		if (entity instanceof PlayerEntity player) {
			if (ModuleManager.antiBot.isEnabled() && AntiBot.bots.contains(entity))
				return true;
			if (player == mc.player || Managers.FRIEND.isFriend(player))
				return true;
			if (player.isCreative() && ignoreCreative.getValue())
				return true;
			if (player.getArmor() == 0 && ignoreNaked.getValue())
				return true;
			if (player.isInvisible() && ignoreInvisible.getValue())
				return true;
			if (player.getTeamColorValue() == mc.player.getTeamColorValue() && ignoreTeam.getValue() && mc.player.getTeamColorValue() != 16777215)
				return true;
		}

		return !isInRange(entity) || (entity.hasCustomName() && ignoreNamed.getValue());
	}

	private boolean isBullet(Entity entity) {
		return (entity instanceof ShulkerBulletEntity || entity instanceof FireballEntity)
			   && entity.isAlive()
			   && PlayerUtility.squaredDistanceFromEyes(entity.getPos()) < getSquaredRotateDistance()
			   && Projectiles.getValue();
	}

	private boolean skipNotSelected(Entity entity) {
		if (entity instanceof SlimeEntity && !Slimes.getValue()) return true;
		if (entity instanceof HostileEntity he) {
			if (!hostiles.getValue())
				return true;

			if (onlyAngry.getValue())
				return !he.isAngryAt(mc.player);
		}

		if (entity instanceof PlayerEntity && !Players.getValue()) return true;
		if (entity instanceof VillagerEntity && !Villagers.getValue()) return true;
		if (entity instanceof MobEntity && !Mobs.getValue()) return true;
		return entity instanceof AnimalEntity && !Animals.getValue();
	}

	private float getFOVAngle(@NotNull LivingEntity e) {
		double difX = e.getX() - mc.player.getX();
		double difZ = e.getZ() - mc.player.getZ();
		float yaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(difZ, difX)) - 90.0);
		return Math.abs(yaw - MathHelper.wrapDegrees(mc.player.getYaw()));
	}

	public void pause() {
		pauseTimer.reset();
	}

	private boolean shouldRandomizeDelay() {
		return randomHitDelay.getValue() && (mc.player.isOnGround() || mc.player.fallDistance < 0.12f || mc.player.isSwimming() || mc.player.isFallFlying());
	}

	private boolean shouldRandomizeFallDistance() {
		return randomHitDelay.getValue() && !shouldRandomizeDelay();
	}

	public static class Position {
		private double x, y, z;
		private int ticks;

		public Position(double x, double y, double z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}

		public boolean shouldRemove() {
			return ticks++ > ModuleManager.aura.backTicks.getValue();
		}

		public double getX() {
			return x;
		}

		public double getY() {
			return y;
		}

		public double getZ() {
			return z;

		}
	}

	public enum RayTrace {
		OFF, OnlyTarget, AllEntities
	}

	public static enum SprintMode {
		HVH,
		Legit,
		None;
	}

	public enum Sort {
		LowestDistance, HighestDistance, LowestHealth, HighestHealth, LowestDurability, HighestDurability, FOV
	}

	public enum Switch {
		Normal, None, Silent
	}

	public enum Resolver {
		Off, Advantage, Predictive, BackTrack
	}

	public enum Mode {
		Interact, Track, Grim, None
	}

	public enum AttackHand {
		MainHand, OffHand, None
	}

	public enum ESP {
		Off, ThunderHack, NurikZapen, CelkaPasta, NextGen, Dimasik
	}

	public enum AccelerateOnHit {
		Off, Yaw, Pitch, Both
	}

	public enum WallsBypass {
		Off, V1, V2
	}
}
