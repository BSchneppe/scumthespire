package battleaimod.patches;

import basemod.ReflectionHacks;
import battleaimod.BattleAiMod;
import battleaimod.battleai.BattleAiController;
import battleaimod.fastobjects.actions.DrawCardActionFast;
import battleaimod.fastobjects.actions.EmptyDeckShuffleActionFast;
import battleaimod.fastobjects.actions.RollMoveActionFast;
import battleaimod.savestate.CardState;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.actions.IntentFlashAction;
import com.megacrit.cardcrawl.actions.animations.SetAnimationAction;
import com.megacrit.cardcrawl.actions.common.*;
import com.megacrit.cardcrawl.actions.defect.TriggerEndOfTurnOrbsAction;
import com.megacrit.cardcrawl.actions.unique.ArmamentsAction;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.audio.MusicMaster;
import com.megacrit.cardcrawl.blights.AbstractBlight;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.cards.tempCards.Shiv;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.OverlayMenu;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.daily.mods.Careless;
import com.megacrit.cardcrawl.daily.mods.ControlledChaos;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.File;
import com.megacrit.cardcrawl.helpers.ModHelper;
import com.megacrit.cardcrawl.helpers.TipTracker;
import com.megacrit.cardcrawl.helpers.input.DevInputActionSet;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.monsters.MonsterGroup;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.relics.BottledFlame;
import com.megacrit.cardcrawl.relics.UnceasingTop;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.rooms.MonsterRoom;
import com.megacrit.cardcrawl.rooms.MonsterRoomBoss;
import com.megacrit.cardcrawl.rooms.MonsterRoomElite;
import com.megacrit.cardcrawl.saveAndContinue.SaveAndContinue;
import com.megacrit.cardcrawl.saveAndContinue.SaveFile;
import com.megacrit.cardcrawl.screens.CombatRewardScreen;
import com.megacrit.cardcrawl.screens.DeathScreen;
import com.megacrit.cardcrawl.unlock.UnlockTracker;
import com.megacrit.cardcrawl.vfx.*;
import com.megacrit.cardcrawl.vfx.cardManip.*;
import com.megacrit.cardcrawl.vfx.combat.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

import static battleaimod.patches.MonsterPatch.shouldGoFast;
import static com.megacrit.cardcrawl.dungeons.AbstractDungeon.actionManager;

public class FastActionsPatch {
    private static final Set<Class> BAD_EFFECTS = new HashSet<Class>() {{
        add(StrikeEffect.class);
        add(HealPanelEffect.class);
        add(PingHpEffect.class);
        add(FlashAtkImgEffect.class);
        add(RefreshEnergyEffect.class);
        add(HbBlockBrokenEffect.class);
        add(BlockImpactLineEffect.class);
        add(BlockedWordEffect.class);
        add(FlashIntentEffect.class);
        add(PowerExpireTextEffect.class);
        add(FlashPowerEffect.class);
        add(PowerDebuffEffect.class);
        add(PowerBuffEffect.class);
        add(ExhaustCardEffect.class);
        add(DeckPoofParticle.class);
        add(HealNumberEffect.class);
        add(GameSavedEffect.class);
        add(DeckPoofEffect.class);
        add(HealEffect.class);
        add(HealVerticalLineEffect.class);
        add(PlayerTurnEffect.class);
        add(EnemyTurnEffect.class);
        add(CardPoofParticle.class);
        add(TextAboveCreatureEffect.class);
        add(SpeechTextEffect.class);
        add(SpeechBubble.class);
        add(BorderFlashEffect.class);
        add(RelicAboveCreatureEffect.class);
        add(GainPennyEffect.class);
        add(ExhaustPileParticle.class);
        add(MegaSpeechBubble.class);
        add(MegaDialogTextEffect.class);
        add(ThoughtBubble.class);
        add(ShowCardAndObtainEffect.class);
        add(GenericSmokeEffect.class);
        add(MoveNameEffect.class);

        // Important stuff happens during construction so we don't have to update, just remove
        add(ShowCardAndAddToDiscardEffect.class);
        add(ShowCardAndAddToDrawPileEffect.class);
        add(ShowCardAndAddToHandEffect.class);

        add(CardPoofEffect.class);
    }};


    private static final Set<Class> GOOD_EFFECTS = new HashSet<Class>() {{

    }};

    static long updateStartTime = 0;

    @SpirePatch(
            clz = AbstractDungeon.class,
            paramtypez = {},
            method = "update"
    )
    public static class StartSpyingPatch {
        public static void Prefix(AbstractDungeon dungeon) {

        }
    }


    @SpirePatch(
            clz = AbstractDungeon.class,
            paramtypez = {},
            method = "update"
    )
    public static class ForceGameActionsPatch {
        public static void Postfix(AbstractDungeon dungeon) {
            long updateStartTime = System.currentTimeMillis();
            GameActionManager actionManager = AbstractDungeon.actionManager;
            if (shouldGoFast()) {
                if (actionManager.phase == GameActionManager.Phase.EXECUTING_ACTIONS || !actionManager.monsterQueue
                        .isEmpty() || shouldStepAiController()) {

                    while (shouldWaitOnActions(actionManager) || shouldStepAiController()) {
                        long startTime = System.currentTimeMillis();

                        clearEffects(AbstractDungeon.topLevelEffects);
                        clearEffects(AbstractDungeon.effectList);
                        clearEffects(AbstractDungeon.effectsQueue);

                        // TODO this is going to have consequences
                        actionManager.cardsPlayedThisCombat.clear();

                        if (shouldWaitOnActions(actionManager)) {
                            while (actionManager.currentAction != null && !AbstractDungeon.isScreenUp) {
                                if (actionManager.currentAction instanceof RollMoveAction) {
                                    AbstractMonster monster = ReflectionHacks
                                            .getPrivate(actionManager.currentAction, RollMoveAction.class, "monster");
                                    actionManager.currentAction = new RollMoveActionFast(monster);
                                } else if (actionManager.currentAction instanceof DrawCardAction) {
                                    actionManager.currentAction = new DrawCardActionFast(AbstractDungeon.player, actionManager.currentAction.amount);
                                } else if (actionManager.currentAction instanceof SetAnimationAction) {
                                    actionManager.currentAction = null;
                                } else if (actionManager.currentAction instanceof EmptyDeckShuffleAction) {
                                    actionManager.currentAction = new EmptyDeckShuffleActionFast();
                                } else if (actionManager.currentAction instanceof ShowMoveNameAction) {
                                    actionManager.currentAction = null;
                                } else if (actionManager.currentAction instanceof WaitAction) {
                                    actionManager.currentAction = null;
                                }
                                if (actionManager.currentAction != null) {
                                    long actionStartTime = System.currentTimeMillis();
                                    Class actionClass = actionManager.currentAction.getClass();

                                    if (!actionManager.currentAction.isDone && !AbstractDungeon.isScreenUp) {
                                        actionManager.currentAction.update();
                                    }
                                    if (BattleAiMod.battleAiController != null && BattleAiMod.battleAiController.actionClassTimes != null) {
                                        long timeThisAction = (System
                                                .currentTimeMillis() - actionStartTime);
                                        BattleAiMod.battleAiController
                                                .addRuntime("Actions", timeThisAction);
                                        HashMap<Class, Long> actionClassTimes = BattleAiMod.battleAiController.actionClassTimes;
                                        if (actionClassTimes != null) {
                                            if (actionClassTimes.containsKey(actionClass)) {
                                                actionClassTimes.put(actionClass, actionClassTimes
                                                        .get(actionClass) + timeThisAction);
                                            } else {
                                                actionClassTimes.put(actionClass, timeThisAction);
                                            }
                                        }
                                    }
                                }

                                if (actionManager.currentAction != null && actionManager.currentAction.isDone && !(actionManager.currentAction instanceof ArmamentsAction)) {
                                    actionManager.currentAction = null;
                                }

                                long actionManagerUpdateTime = System.currentTimeMillis();

                                if (!AbstractDungeon.isScreenUp) {
                                    actionManager.update();
                                }
                                BattleAiMod.battleAiController
                                        .addRuntime("Action Manager Loop", System
                                                .currentTimeMillis() - actionManagerUpdateTime);
                            }
                        } else if (shouldStepAiController()) {
                            long stepStartTime = System.currentTimeMillis();

                            BattleAiMod.readyForUpdate = false;
                            BattleAiMod.battleAiController.step();

                            BattleAiMod.battleAiController.addRuntime("Battle AI Step", System
                                    .currentTimeMillis() - stepStartTime);
                        }

                        long startRoomUpdate = System.currentTimeMillis();

//                        System.err.println("Updating room");
                        //actionManager.update();
                        if (!AbstractDungeon.isScreenUp) {
                            roomUpdate();
                        }

                        if (BattleAiMod.battleAiController != null) {
                            BattleAiMod.battleAiController.addRuntime("Room Update", System
                                    .currentTimeMillis() - startRoomUpdate);
                        }

                        if (AbstractDungeon.player.currentHealth <= 0) {
                            if (actionManager.currentAction instanceof DamageAction) {
                                actionManager.update();
                            }
//                            break;
                        }

                        if (BattleAiMod.battleAiController != null) {
                            BattleAiMod.battleAiController.addRuntime("Update Loop Total", System
                                    .currentTimeMillis() - startTime);
                        }

                        if (actionManager.phase == GameActionManager.Phase.WAITING_ON_USER && actionManager.currentAction == null) {
                            BattleAiMod.readyForUpdate = true;
                        }
                    }

                    System.err
                            .println("exiting loop " + actionManager.currentAction + " " + actionManager.phase + " " + AbstractDungeon.effectList
                                    .size() + " " + actionManager.actions.size()
                                    + " " + AbstractDungeon.topLevelEffects
                                    .size() + " " + AbstractDungeon.effectsQueue
                                    .size() + " " + actionManager.monsterQueue.size());
                    if (actionManager.currentAction == null && !AbstractDungeon.isScreenUp) {
                        ActionManagerNextAction();
                        AbstractDungeon
                                .getCurrRoom().phase = AbstractRoom.RoomPhase.COMBAT;
                    }

                }
            }
        }
    }

    @SpirePatch(
            clz = RemoveSpecificPowerAction.class,
            paramtypez = {},
            method = "update"
    )
    public static class FastRemovePowerPatch {
        public static void Prefix(RemoveSpecificPowerAction _instance) {
            if (shouldGoFast()) {
                ReflectionHacks
                        .setPrivate(_instance, AbstractGameAction.class, "duration", .1F);
            }
        }

        public static void Postfix(RemoveSpecificPowerAction _instance) {
            if (shouldGoFast()) {
                _instance.isDone = true;
            }
        }
    }

    @SpirePatch(
            clz = ApplyPowerAction.class,
            paramtypez = {},
            method = "update"
    )
    public static class FastApplyPowerActionPatch {
        public static void Prefix(ApplyPowerAction _instance) {
            if (shouldGoFast()) {
                ReflectionHacks
                        .setPrivate(_instance, AbstractGameAction.class, "duration", .1F);
            }
        }

        public static void Postfix(ApplyPowerAction _instance) {
            if (shouldGoFast()) {
                _instance.isDone = true;
            }
        }
    }

    @SpirePatch(
            clz = AbstractRoom.class,
            paramtypez = {},
            method = "endBattle"
    )
    public static class EndBattlePatch {
        public static void Postfix(AbstractRoom _instance) {
            if (shouldGoFast()) {
                BattleAiMod.readyForUpdate = true;
            }
        }
    }

    @SpirePatch(
            clz = SaveFile.class,
            paramtypez = {SaveFile.SaveType.class},
            method = SpirePatch.CONSTRUCTOR
    )
    public static class NoMakeSavePatch {
        public static SpireReturn Prefix(SaveFile _instance, SaveFile.SaveType type) {
            if (shouldGoFast()) {
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }

    @SpirePatch(
            clz = SaveAndContinue.class,
            paramtypez = {SaveFile.class},
            method = "save"
    )
    public static class NoSavingPatch {
        public static SpireReturn Prefix(SaveFile save) {
            if (shouldGoFast()) {
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }

    @SpirePatch(
            clz = File.class,
            paramtypez = {},
            method = "save"
    )
    public static class NoSavingOnOtherThreadPatch {
        public static SpireReturn Prefix(File _instance) {
            if (shouldGoFast()) {
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }

    @SpirePatch(
            clz = MakeTempCardInDiscardAction.class,
            paramtypez = {},
            method = "update"
    )
    public static class MakeTempCardsFastPatch {
        public static void Prefix(MakeTempCardInDiscardAction _instance) {
            if (shouldGoFast()) {
                ReflectionHacks
                        .setPrivate(_instance, AbstractGameAction.class, "duration", .00001F);

                ReflectionHacks
                        .setPrivate(_instance, AbstractGameAction.class, "startDuration", .00001F);
            }
        }

        public static void Postfix(MakeTempCardInDiscardAction _instance) {
            if (shouldGoFast()) {
                _instance.isDone = true;
                CardState.freeCard(ReflectionHacks
                        .getPrivate(_instance, MakeTempCardInDiscardAction.class, "c"));
            }
        }
    }

    @SpirePatch(
            clz = GainBlockAction.class,
            paramtypez = {},
            method = "update"
    )
    public static class GainBlockActionFastPatch {
        public static void Prefix(GainBlockAction _instance) {
            if (shouldGoFast()) {
                ReflectionHacks
                        .setPrivate(_instance, AbstractGameAction.class, "duration", .001F);

                ReflectionHacks
                        .setPrivate(_instance, AbstractGameAction.class, "startDuration", .001F);
            }
        }

        public static void Postfix(GainBlockAction _instance) {
            if (shouldGoFast()) {
                _instance.isDone = true;
            }
        }
    }

    public static boolean shouldStepAiController() {
        return (BattleAiMod.battleAiController != null &&
                !BattleAiMod.battleAiController.isDone) &&
                ((actionManager.phase == GameActionManager.Phase.WAITING_ON_USER &&
                        !BattleAiMod.battleAiController.runCommandMode &&
                        actionManager.currentAction == null &&
                        actionManager.actions.isEmpty() &&
                        actionManager.cardQueue.isEmpty() &&
                        !actionManager.usingCard) ||
                        AbstractDungeon.isScreenUp);
    }

    private static boolean shouldWaitOnActions(GameActionManager actionManager) {
        return (!AbstractDungeon.isScreenUp && BattleAiMod.battleAiController != null && !BattleAiMod.battleAiController.runCommandMode && !AbstractDungeon.isScreenUp) &&
                ((actionManager.currentAction != null) || (actionManager.turnHasEnded && !AbstractDungeon
                        .getMonsters().areMonstersBasicallyDead()) ||
                        !actionManager.monsterQueue.isEmpty() || (!actionManager.actions
                        .isEmpty() && AbstractDungeon.screen != AbstractDungeon.CurrentScreen.HAND_SELECT) || actionManager.usingCard);
    }


    @SpirePatch(
            clz = MonsterRoom.class,
            paramtypez = {},
            method = "onPlayerEntry"
    )
    public static class SpyOnMonsterRoomPatch {
        public static void Prefix(MonsterRoom _instance) {
            System.err.println("Starting fight " + AbstractDungeon.monsterList.get(0));
            BattleAiController.currentEncounter = AbstractDungeon.monsterList.get(0);
        }


    }

    @SpirePatch(
            clz = MonsterRoomElite.class,
            paramtypez = {},
            method = "onPlayerEntry"
    )
    public static class SpyOnEliteMonsterRoomPatch {
        public static void Prefix(MonsterRoomElite _instance) {
            System.err.println("Starting fight " + AbstractDungeon.eliteMonsterList.get(0));
            BattleAiController.currentEncounter = AbstractDungeon.eliteMonsterList.get(0);
        }
    }

    @SpirePatch(
            clz = MonsterRoomBoss.class,
            paramtypez = {},
            method = "onPlayerEntry"
    )
    public static class SpyOnBossMonsterRoomPatch {
        public static void Prefix(MonsterRoomBoss _instance) {
            System.err.println("Starting fight " + AbstractDungeon.bossList.get(0));
            BattleAiController.currentEncounter = AbstractDungeon.bossList.get(0);
        }
    }

    // TODO this is a hack to fix an NPE
    @SpirePatch(
            clz = BottledFlame.class,
            paramtypez = {},
            method = "setDescriptionAfterLoading"
    )
    public static class FixDescriptionNPE {
        public static void Replace(BottledFlame _instance) {

        }
    }

    @SpirePatch(
            clz = AbstractPlayer.class,
            paramtypez = {},
            method = "draw"
    )
    public static class NoSoundDrawPatch {
        public static void Replace(AbstractPlayer _instance) {
            if (_instance.hand.size() == 10) {
                _instance.createHandIsFullDialog();
            } else {
                _instance.draw(1);
                _instance.onCardDrawOrDiscard();
            }
        }
    }

    @SpirePatch(
            clz = DeathScreen.class,
            paramtypez = {MonsterGroup.class},
            method = SpirePatch.CONSTRUCTOR
    )
    public static class DisableDeathScreenpatch {
        public static SpireReturn Prefix(DeathScreen _instance, MonsterGroup monsterGroup) {
            if (shouldGoFast()) {
                BattleAiMod.readyForUpdate = true;
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }

    @SpirePatch(
            clz = StrikeEffect.class,
            paramtypez = {AbstractCreature.class, float.class, float.class, int.class},
            method = SpirePatch.CONSTRUCTOR
    )
    public static class TooManyLinesPatch {
        public static SpireReturn Prefix(StrikeEffect _instance, AbstractCreature target, float x, float y, int number) {
            if (shouldGoFast()) {
                _instance.isDone = true;
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }

    @SpirePatch(
            clz = AbstractRoom.class,
            paramtypez = {},
            method = "addPotionToRewards"
    )
    public static class PotionRemovePatch {
        public static SpireReturn Prefix(AbstractRoom _instance) {
            if (shouldGoFast()) {
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }

    @SpirePatch(
            clz = OverlayMenu.class,
            paramtypez = {},
            method = "showCombatPanels"
    )
    public static class TooMdddanyLinesPatch {
        public static SpireReturn Prefix(OverlayMenu _instance) {
            if (shouldGoFast()) {
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }

    @SpirePatch(
            clz = DamageAction.class,
            paramtypez = {},
            method = "update"
    )
    public static class ForceDamageActionPatch {
        public static void Prefix(DamageAction _instance) {
            _instance.isDone = true;
        }
    }

//    @SpirePatch(
//            clz = ExhaustAction.class,
//            paramtypez = {},
//            method = "update"
//    )
//    public static class FastExhaustPatch {
//        public static void Prefix(ExhaustAction _instance) {
//            _instance.isDone = true;
//        }
//    }

    // THIS IS VOODOO, DON'T TOUCH IT
    @SpirePatch(
            clz = AbstractPlayer.class,
            paramtypez = {int.class},
            method = "draw"
    )
    public static class NoSoundDrawPatch2 {
        private static final Logger logger = LogManager.getLogger(AbstractPlayer.class.getName());

        public static void Replace(AbstractPlayer _instance, int numCards) {
            for (int i = 0; i < numCards; ++i) {
                if (_instance.drawPile.isEmpty()) {
                    logger.info("ERROR: How did this happen? No cards in draw pile?? Player.java");
                } else {
                    AbstractCard c = _instance.drawPile.getTopCard();
                    c.current_x = CardGroup.DRAW_PILE_X;
                    c.current_y = CardGroup.DRAW_PILE_Y;
                    c.setAngle(0.0F, true);
                    c.lighten(false);
                    c.drawScale = 0.12F;
                    c.targetDrawScale = 0.75F;
                    c.triggerWhenDrawn();
                    _instance.hand.addToHand(c);
                    _instance.drawPile.removeTopCard();
                    Iterator var4 = _instance.powers.iterator();

                    while (var4.hasNext()) {
                        AbstractPower p = (AbstractPower) var4.next();
                        p.onCardDraw(c);
                    }

                    var4 = _instance.relics.iterator();

                    while (var4.hasNext()) {
                        AbstractRelic r = (AbstractRelic) var4.next();
                        r.onCardDraw(c);
                    }
                }
            }
        }
    }

    static void roomUpdate() {
        AbstractDungeon.getCurrRoom().monsters.update();
        if (!(AbstractDungeon.getCurrRoom().waitTimer > 0.0F)) {
            long startTopCondition = System.currentTimeMillis();

            if (Settings.isDebug && DevInputActionSet.drawCard.isJustPressed()) {
                AbstractDungeon.actionManager
                        .addToTop(new DrawCardAction(AbstractDungeon.player, 1));
            }

            long midTopCondition = System.currentTimeMillis();

            if (!AbstractDungeon.isScreenUp) {
//                System.err.println("updating room " + actionManager.cardsPlayedThisCombat
//                        .size() + " " + actionManager.cardsPlayedThisTurn.size());

                ActionManageUpdate();
                if (!AbstractDungeon.getCurrRoom().monsters
                        .areMonstersBasicallyDead() && AbstractDungeon.player.currentHealth > 0) {
                    AbstractDungeon.player.updateInput();
                }
            }


            if (!AbstractDungeon.screen.equals(AbstractDungeon.CurrentScreen.HAND_SELECT)) {
                AbstractDungeon.player.combatUpdate();
            }

            if (AbstractDungeon.player.isEndingTurn) {
                AbstractDungeon.getCurrRoom().endTurn();
            }


            if (BattleAiMod.battleAiController != null) {
//                BattleAiMod.battleAiController.addRuntime("Top Condition", System
//                        .currentTimeMillis() - startTopCondition);
//                BattleAiMod.battleAiController.addRuntime("Mid Top Condition", System
//                        .currentTimeMillis() - midTopCondition);
            }

        } else {
            if (AbstractDungeon.actionManager.currentAction == null && AbstractDungeon.actionManager
                    .isEmpty()) {
                AbstractDungeon.getCurrRoom().waitTimer -= Gdx.graphics.getDeltaTime();
            } else {
                AbstractDungeon.actionManager.update();
            }

            if (AbstractDungeon.getCurrRoom().waitTimer <= 0.0F) {
                AbstractDungeon.actionManager.turnHasEnded = true;
                if (!AbstractDungeon.isScreenUp) {
                    AbstractDungeon.topLevelEffects.add(new BattleStartEffect(false));
                }

                AbstractDungeon.actionManager
                        .addToBottom(new GainEnergyAndEnableControlsAction(AbstractDungeon.player.energy.energyMaster));
                AbstractDungeon.player.applyStartOfCombatPreDrawLogic();
                AbstractDungeon.actionManager
                        .addToBottom(new DrawCardActionFast(AbstractDungeon.player, AbstractDungeon.player.gameHandSize));
                AbstractDungeon.actionManager.addToBottom(new EnableEndTurnButtonAction());
                AbstractDungeon.overlayMenu.showCombatPanels();
                AbstractDungeon.player.applyStartOfCombatLogic();
                if (ModHelper.isModEnabled("Careless")) {
                    Careless.modAction();
                }

                if (ModHelper.isModEnabled("ControlledChaos")) {
                    ControlledChaos.modAction();
                }

                AbstractDungeon.getCurrRoom().skipMonsterTurn = false;
                AbstractDungeon.player.applyStartOfTurnRelics();
                AbstractDungeon.player.applyStartOfTurnPostDrawRelics();
                AbstractDungeon.player.applyStartOfTurnCards();
                AbstractDungeon.player.applyStartOfTurnPowers();
                AbstractDungeon.player.applyStartOfTurnOrbs();
                AbstractDungeon.actionManager.useNextCombatActions();
            }
        }

        if (AbstractDungeon.getCurrRoom().isBattleOver && AbstractDungeon.actionManager.actions
                .isEmpty()) {

            // There was stuff here, hopefully we don't need it
        }

        AbstractDungeon.getCurrRoom().monsters.updateAnimations();
    }

    public static void ActionManageUpdate() {
        long localManagerUpdateStart = System.currentTimeMillis();
        switch (AbstractDungeon.actionManager.phase) {
            case WAITING_ON_USER:
                ActionManagerNextAction();
                break;
            case EXECUTING_ACTIONS:
                if (AbstractDungeon.actionManager.currentAction != null && !AbstractDungeon.actionManager.currentAction.isDone) {
                    if (actionManager.currentAction instanceof DrawCardAction) {
                        actionManager.currentAction = new DrawCardActionFast(AbstractDungeon.player, actionManager.currentAction.amount);
                    }
                    AbstractDungeon.actionManager.currentAction.update();
                } else {
                    AbstractDungeon.actionManager.previousAction = AbstractDungeon.actionManager.currentAction;
                    AbstractDungeon.actionManager.currentAction = null;
                    ActionManagerNextAction();
                    if (AbstractDungeon.actionManager.currentAction == null && AbstractDungeon
                            .getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT && !actionManager.usingCard) {
                        AbstractDungeon.actionManager.phase = GameActionManager.Phase.WAITING_ON_USER;
                        AbstractDungeon.player.hand.refreshHandLayout();
                        AbstractDungeon.actionManager.hasControl = false;
                    }

                    AbstractDungeon.actionManager.usingCard = false;
                }
                break;
            default:
        }

        if (BattleAiMod.battleAiController != null) {
            BattleAiMod.battleAiController.addRuntime("Local Manager Update", System
                    .currentTimeMillis() - localManagerUpdateStart);
        }

    }


    public static void ActionManagerNextAction() {
        long localManagerNextAction = System.currentTimeMillis();
        if (!AbstractDungeon.actionManager.actions.isEmpty()) {
            AbstractDungeon.actionManager.currentAction = AbstractDungeon.actionManager.actions
                    .remove(0);
            AbstractDungeon.actionManager.phase = GameActionManager.Phase.EXECUTING_ACTIONS;
            AbstractDungeon.actionManager.hasControl = true;
        } else if (!AbstractDungeon.actionManager.preTurnActions.isEmpty()) {
            AbstractDungeon.actionManager.currentAction = AbstractDungeon.actionManager.preTurnActions
                    .remove(0);
            AbstractDungeon.actionManager.phase = GameActionManager.Phase.EXECUTING_ACTIONS;
            AbstractDungeon.actionManager.hasControl = true;
        } else if (!AbstractDungeon.actionManager.cardQueue.isEmpty()) {
            long startCardStuff = System.currentTimeMillis();

            AbstractDungeon.actionManager.usingCard = true;
            AbstractCard c = AbstractDungeon.actionManager.cardQueue.get(0).card;
            if (c == null) {
                callEndOfTurnActions();
            } else if (c.equals(AbstractDungeon.actionManager.lastCard)) {
                AbstractDungeon.actionManager.lastCard = null;
            }

            if (AbstractDungeon.actionManager.cardQueue
                    .size() == 1 && AbstractDungeon.actionManager.cardQueue
                    .get(0).isEndTurnAutoPlay) {
                AbstractRelic top = AbstractDungeon.player.getRelic("Unceasing Top");
                if (top != null) {
                    ((UnceasingTop) top).disableUntilTurnEnds();
                }
            }

            boolean canPlayCard = false;
            if (c != null) {
                c.isInAutoplay = AbstractDungeon.actionManager.cardQueue
                        .get(0).autoplayCard;
            }

            if (c != null && AbstractDungeon.actionManager.cardQueue
                    .get(0).randomTarget) {
                AbstractDungeon.actionManager.cardQueue
                        .get(0).monster = AbstractDungeon.getMonsters()
                                                         .getRandomMonster(null, true, AbstractDungeon.cardRandomRng);
            }

            Iterator i;
            AbstractCard card;
            if (AbstractDungeon.actionManager.cardQueue.get(0).card == null || !c
                    .canUse(AbstractDungeon.player, actionManager.cardQueue
                            .get(0).monster) && !actionManager.cardQueue
                    .get(0).card.dontTriggerOnUseCard) {
                i = AbstractDungeon.player.limbo.group.iterator();

                while (i.hasNext()) {
                    card = (AbstractCard) i.next();
                    if (card == c) {
                        c.fadingOut = true;
                        AbstractDungeon.effectList.add(new ExhaustCardEffect(c));
                        i.remove();
                    }
                }

                if (c != null) {
                    AbstractDungeon.effectList
                            .add(new ThoughtBubble(AbstractDungeon.player.dialogX, AbstractDungeon.player.dialogY, 3.0F, c.cantUseMessage, true));
                }
            } else {
                canPlayCard = true;
                if (c.freeToPlay()) {
                    c.freeToPlayOnce = true;
                }

                AbstractDungeon.actionManager.cardQueue
                        .get(0).card.energyOnUse = AbstractDungeon.actionManager.cardQueue
                        .get(0).energyOnUse;
                if (c.isInAutoplay) {
                    AbstractDungeon.actionManager.cardQueue
                            .get(0).card.ignoreEnergyOnUse = true;
                } else {
                    AbstractDungeon.actionManager.cardQueue
                            .get(0).card.ignoreEnergyOnUse = AbstractDungeon.actionManager.cardQueue
                            .get(0).ignoreEnergyTotal;
                }

                if (!AbstractDungeon.actionManager.cardQueue
                        .get(0).card.dontTriggerOnUseCard) {
                    i = AbstractDungeon.player.powers.iterator();

                    while (i.hasNext()) {
                        AbstractPower p = (AbstractPower) i.next();
                        p.onPlayCard(AbstractDungeon.actionManager.cardQueue
                                .get(0).card, AbstractDungeon.actionManager.cardQueue
                                .get(0).monster);
                    }

                    i = AbstractDungeon.getMonsters().monsters.iterator();

                    while (i.hasNext()) {
                        AbstractMonster m = (AbstractMonster) i.next();
                        Iterator var5 = m.powers.iterator();

                        while (var5.hasNext()) {
                            AbstractPower p = (AbstractPower) var5.next();
                            p.onPlayCard(AbstractDungeon.actionManager.cardQueue
                                    .get(0).card, AbstractDungeon.actionManager.cardQueue
                                    .get(0).monster);
                        }
                    }

                    i = AbstractDungeon.player.relics.iterator();

                    while (i.hasNext()) {
                        AbstractRelic r = (AbstractRelic) i.next();
                        r.onPlayCard(AbstractDungeon.actionManager.cardQueue
                                .get(0).card, AbstractDungeon.actionManager.cardQueue
                                .get(0).monster);
                    }

                    AbstractDungeon.player.stance
                            .onPlayCard(AbstractDungeon.actionManager.cardQueue
                                    .get(0).card);
                    i = AbstractDungeon.player.blights.iterator();

                    while (i.hasNext()) {
                        AbstractBlight b = (AbstractBlight) i.next();
                        b.onPlayCard(AbstractDungeon.actionManager.cardQueue
                                .get(0).card, AbstractDungeon.actionManager.cardQueue
                                .get(0).monster);
                    }

                    i = AbstractDungeon.player.hand.group.iterator();

                    while (i.hasNext()) {
                        card = (AbstractCard) i.next();
                        card.onPlayCard(AbstractDungeon.actionManager.cardQueue
                                .get(0).card, AbstractDungeon.actionManager.cardQueue
                                .get(0).monster);
                    }

                    i = AbstractDungeon.player.discardPile.group.iterator();

                    while (i.hasNext()) {
                        card = (AbstractCard) i.next();
                        card.onPlayCard(AbstractDungeon.actionManager.cardQueue
                                .get(0).card, AbstractDungeon.actionManager.cardQueue
                                .get(0).monster);
                    }

                    i = AbstractDungeon.player.drawPile.group.iterator();

                    while (i.hasNext()) {
                        card = (AbstractCard) i.next();
                        card.onPlayCard(AbstractDungeon.actionManager.cardQueue
                                .get(0).card, AbstractDungeon.actionManager.cardQueue
                                .get(0).monster);
                    }

                    ++AbstractDungeon.player.cardsPlayedThisTurn;
                    AbstractDungeon.actionManager.cardsPlayedThisTurn
                            .add(AbstractDungeon.actionManager.cardQueue
                                    .get(0).card);
                    AbstractDungeon.actionManager.cardsPlayedThisCombat
                            .add(AbstractDungeon.actionManager.cardQueue
                                    .get(0).card);
                }

                if (AbstractDungeon.actionManager.cardsPlayedThisTurn.size() == 25) {
                    UnlockTracker.unlockAchievement("INFINITY");
                }

                if (AbstractDungeon.actionManager.cardsPlayedThisTurn
                        .size() >= 20 && !CardCrawlGame.combo) {
                    CardCrawlGame.combo = true;
                }

                if (AbstractDungeon.actionManager.cardQueue
                        .get(0).card instanceof Shiv) {
                    int shivCount = 0;
                    Iterator var15 = AbstractDungeon.actionManager.cardsPlayedThisTurn.iterator();

                    while (var15.hasNext()) {
                        AbstractCard card2 = (AbstractCard) var15.next();
                        if (card2 instanceof Shiv) {
                            ++shivCount;
                            if (shivCount == 10) {
                                UnlockTracker.unlockAchievement("NINJA");
                                break;
                            }
                        }
                    }
                }

                if (AbstractDungeon.actionManager.cardQueue.get(0).card != null) {
                    if (AbstractDungeon.actionManager.cardQueue
                            .get(0).card.target != AbstractCard.CardTarget.ENEMY || AbstractDungeon.actionManager.cardQueue
                            .get(0).monster != null && !AbstractDungeon.actionManager.cardQueue
                            .get(0).monster.isDeadOrEscaped()) {
                        AbstractDungeon.player
                                .useCard(AbstractDungeon.actionManager.cardQueue
                                        .get(0).card, AbstractDungeon.actionManager.cardQueue
                                        .get(0).monster, AbstractDungeon.actionManager.cardQueue
                                        .get(0).energyOnUse);
                    } else {
                        i = AbstractDungeon.player.limbo.group.iterator();

                        while (i.hasNext()) {
                            card = (AbstractCard) i.next();
                            if (card == AbstractDungeon.actionManager.cardQueue
                                    .get(0).card) {
                                AbstractDungeon.actionManager.cardQueue
                                        .get(0).card.fadingOut = true;
                                AbstractDungeon.effectList
                                        .add(new ExhaustCardEffect(AbstractDungeon.actionManager.cardQueue
                                                .get(0).card));
                                i.remove();
                            }
                        }

                        if (AbstractDungeon.actionManager.cardQueue
                                .get(0).monster == null) {
                            AbstractDungeon.actionManager.cardQueue
                                    .get(0).card.drawScale = AbstractDungeon.actionManager.cardQueue
                                    .get(0).card.targetDrawScale;
                            AbstractDungeon.actionManager.cardQueue
                                    .get(0).card.angle = AbstractDungeon.actionManager.cardQueue
                                    .get(0).card.targetAngle;
                            AbstractDungeon.actionManager.cardQueue
                                    .get(0).card.current_x = AbstractDungeon.actionManager.cardQueue
                                    .get(0).card.target_x;
                            AbstractDungeon.actionManager.cardQueue
                                    .get(0).card.current_y = AbstractDungeon.actionManager.cardQueue
                                    .get(0).card.target_y;
                            AbstractDungeon.effectList
                                    .add(new ExhaustCardEffect(AbstractDungeon.actionManager.cardQueue
                                            .get(0).card));
                        }
                    }
                }
            }

            AbstractDungeon.actionManager.cardQueue.remove(0);
            if (!canPlayCard && c != null && c.isInAutoplay) {
                c.dontTriggerOnUseCard = true;
                AbstractDungeon.actionManager.addToBottom(new UseCardAction(c));
            }

            if (BattleAiMod.battleAiController != null) {
                BattleAiMod.battleAiController.addRuntime("Local Action Manager Card Stuff", System
                        .currentTimeMillis() - startCardStuff);
            }

        } else if (!AbstractDungeon.actionManager.monsterAttacksQueued) {
            long startMonsterQueuedStuff = System.currentTimeMillis();

            AbstractDungeon.actionManager.monsterAttacksQueued = true;
            if (!AbstractDungeon.getCurrRoom().skipMonsterTurn) {
                AbstractDungeon.getCurrRoom().monsters.queueMonsters();
            }

            if (BattleAiMod.battleAiController != null) {
                BattleAiMod.battleAiController
                        .addRuntime("Local Action Manager Monster Queued Stuff", System
                                .currentTimeMillis() - startMonsterQueuedStuff);
            }
        } else if (!AbstractDungeon.actionManager.monsterQueue.isEmpty()) {
            long startOtherMonsterQueuedStuff = System.currentTimeMillis();

            AbstractMonster m = AbstractDungeon.actionManager.monsterQueue
                    .get(0).monster;
            if (!m.isDeadOrEscaped() || m.halfDead) {
                if (m.intent != AbstractMonster.Intent.NONE) {
                    AbstractDungeon.actionManager.addToBottom(new ShowMoveNameAction(m));
                    AbstractDungeon.actionManager.addToBottom(new IntentFlashAction(m));
                }

                if (!(Boolean) TipTracker.tips
                        .get("INTENT_TIP") && AbstractDungeon.player.currentBlock == 0 && (m.intent == AbstractMonster.Intent.ATTACK || m.intent == AbstractMonster.Intent.ATTACK_DEBUFF || m.intent == AbstractMonster.Intent.ATTACK_BUFF || m.intent == AbstractMonster.Intent.ATTACK_DEFEND)) {
                    if (AbstractDungeon.floorNum <= 5) {
                        ++TipTracker.blockCounter;
                    } else {
                        TipTracker.neverShowAgain("INTENT_TIP");
                    }
                }

                long monsterTurnStart = System.currentTimeMillis();

                m.takeTurn();
                if (BattleAiMod.battleAiController != null) {
                    BattleAiMod.battleAiController.addRuntime("Monster Turn", System
                            .currentTimeMillis() - monsterTurnStart);
                }

                m.applyTurnPowers();
            }

            AbstractDungeon.actionManager.monsterQueue.remove(0);
            if (AbstractDungeon.actionManager.monsterQueue.isEmpty()) {
                AbstractDungeon.actionManager.addToBottom(new WaitAction(1.5F));
            }

            if (BattleAiMod.battleAiController != null) {
                BattleAiMod.battleAiController
                        .addRuntime("Local Action Manager Other Monster Queued Stuff", System
                                .currentTimeMillis() - startOtherMonsterQueuedStuff);
            }
        } else if (AbstractDungeon.actionManager.turnHasEnded && !AbstractDungeon.getMonsters()
                                                                                 .areMonstersBasicallyDead()) {
            long startEOTStuff = System.currentTimeMillis();

            if (!AbstractDungeon.getCurrRoom().skipMonsterTurn) {
                AbstractDungeon.getCurrRoom().monsters.applyEndOfTurnPowers();
            }

            AbstractDungeon.player.cardsPlayedThisTurn = 0;
            AbstractDungeon.actionManager.orbsChanneledThisTurn.clear();
            if (ModHelper.isModEnabled("Careless")) {
                Careless.modAction();
            }

            if (ModHelper.isModEnabled("ControlledChaos")) {
                ControlledChaos.modAction();
                AbstractDungeon.player.hand.applyPowers();
            }

            long startStartOfTurn = System.currentTimeMillis();

            AbstractDungeon.player.applyStartOfTurnRelics();
            AbstractDungeon.player.applyStartOfTurnPreDrawCards();
            AbstractDungeon.player.applyStartOfTurnCards();
            AbstractDungeon.player.applyStartOfTurnPowers();
            AbstractDungeon.player.applyStartOfTurnOrbs();

            if (BattleAiMod.battleAiController != null) {
                BattleAiMod.battleAiController.addRuntime("EOT Stuff SOT", System
                        .currentTimeMillis() - startStartOfTurn);
            }

            ++GameActionManager.turn;
            AbstractDungeon.getCurrRoom().skipMonsterTurn = false;
            AbstractDungeon.actionManager.turnHasEnded = false;
            GameActionManager.totalDiscardedThisTurn = 0;
            AbstractDungeon.actionManager.cardsPlayedThisTurn.clear();
            GameActionManager.damageReceivedThisTurn = 0;
            if (!AbstractDungeon.player.hasPower("Barricade") && !AbstractDungeon.player
                    .hasPower("Blur")) {
                if (!AbstractDungeon.player.hasRelic("Calipers")) {
                    AbstractDungeon.player.loseBlock();
                } else {
                    AbstractDungeon.player.loseBlock(15);
                }
            }

            long startLastPart = System.currentTimeMillis();

            if (!AbstractDungeon.getCurrRoom().isBattleOver) {
                long checkpoint1 = System.currentTimeMillis();
                AbstractDungeon.actionManager
                        .addToBottom(new DrawCardActionFast(null, AbstractDungeon.player.gameHandSize, true));

                long checkpoint2 = System.currentTimeMillis();
                AbstractDungeon.player.applyStartOfTurnPostDrawRelics();
                long checkpoint3 = System.currentTimeMillis();

                AbstractDungeon.player.applyStartOfTurnPostDrawPowers();
                long checkpoint4 = System.currentTimeMillis();

                AbstractDungeon.actionManager.addToBottom(new EnableEndTurnButtonAction());
                long checkpoint5 = System.currentTimeMillis();

                if (BattleAiMod.battleAiController != null) {
//                    BattleAiMod.battleAiController.addRuntime("EOT part1", checkpoint2 - checkpoint1);
//                    BattleAiMod.battleAiController.addRuntime("EOT part2", checkpoint3 - checkpoint2);
//                    BattleAiMod.battleAiController.addRuntime("EOT part3", checkpoint4 - checkpoint3);
//                    BattleAiMod.battleAiController.addRuntime("EOT part4", checkpoint5 - checkpoint4);
                }
            }

            if (BattleAiMod.battleAiController != null) {
                BattleAiMod.battleAiController.addRuntime("Local Action Manager EOT Stuff", System
                        .currentTimeMillis() - startEOTStuff);
                BattleAiMod.battleAiController.addRuntime("EOT Stuff end part", System
                        .currentTimeMillis() - startLastPart);
            }
        }

        if (BattleAiMod.battleAiController != null) {
            BattleAiMod.battleAiController.addRuntime("Local Manager Next Action", System
                    .currentTimeMillis() - localManagerNextAction);
        }
    }

    private static void callEndOfTurnActions() {
        long localManagerCallEOT = System.currentTimeMillis();

        AbstractDungeon.getCurrRoom().applyEndOfTurnRelics();
        AbstractDungeon.getCurrRoom().applyEndOfTurnPreCardPowers();
        actionManager.addToBottom(new TriggerEndOfTurnOrbsAction());
        Iterator var1 = AbstractDungeon.player.hand.group.iterator();

        while (var1.hasNext()) {
            AbstractCard c = (AbstractCard) var1.next();
            c.triggerOnEndOfTurnForPlayingCard();
        }

        AbstractDungeon.player.stance.onEndOfTurn();

        if (BattleAiMod.battleAiController != null) {
            BattleAiMod.battleAiController.addRuntime("Local Manager Call EOT", System
                    .currentTimeMillis() - localManagerCallEOT);
        }
    }

    @SpirePatch(
            clz = CombatRewardScreen.class,
            paramtypez = {SpriteBatch.class},
            method = "render"
    )
    public static class NoRenderBodyPatch {
        public static SpireReturn Prefix(CombatRewardScreen _instance, SpriteBatch sb) {
            if (shouldGoFast()) {
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }

    @SpirePatch(
            clz = UnlockTracker.class,
            paramtypez = {String.class},
            method = "markCardAsSeen"
    )
    public static class NoUnlockTrackerPatch {
        public static SpireReturn Prefix(String cardName) {
            if (shouldGoFast()) {
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }

    @SpirePatch(
            clz = UnlockTracker.class,
            paramtypez = {String.class},
            method = "hardUnlock"
    )
    public static class NoHardUnlockTrackerPatch {
        public static SpireReturn Prefix(String cardName) {
            if (shouldGoFast()) {
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }

    @SpirePatch(
            clz = UnlockTracker.class,
            paramtypez = {String.class},
            method = "hardUnlockOverride"
    )
    public static class NoHardOverrideUnlockTrackerPatch {
        public static SpireReturn Prefix(String cardName) {
            if (shouldGoFast()) {
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }

    @SpirePatch(
            clz = MusicMaster.class,
            paramtypez = {String.class},
            method = "playTempBGM"
    )
    public static class NoPlayMusicPatch {
        public static SpireReturn Prefix(MusicMaster _instance, String key) {
            if (shouldGoFast()) {
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }

    @SpirePatch(
            clz = MusicMaster.class,
            paramtypez = {String.class},
            method = "playTempBgmInstantly"
    )
    public static class NoPlayMusicPatc2 {
        public static SpireReturn Prefix(MusicMaster _instance, String key) {
            if (shouldGoFast()) {
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }

    @SpirePatch(
            clz = MusicMaster.class,
            paramtypez = {String.class, boolean.class},
            method = "playTempBgmInstantly"
    )
    public static class NoPlayMusicPatch3 {
        public static SpireReturn Prefix(MusicMaster _instance, String key, boolean loop) {
            if (shouldGoFast()) {
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }

    private static void clearEffects(ArrayList<AbstractGameEffect> effects) {
        Iterator<AbstractGameEffect> iterator = effects.iterator();
        while (iterator.hasNext()) {
            Class effectClass = iterator.next().getClass();
            if (BAD_EFFECTS.contains(effectClass)) {
                iterator.remove();
            } else if (!GOOD_EFFECTS.contains(effectClass)) {
                System.err.println("Allowing unknown effect " + effectClass);
            }
        }
    }

}
