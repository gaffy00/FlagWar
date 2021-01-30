
/*
 * Copyright 2021 TownyAdvanced
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.github.townyadvanced.flagwar.listeners;

import com.palmergames.bukkit.towny.Towny;
import com.palmergames.bukkit.towny.TownyEconomyHandler;
import com.palmergames.bukkit.towny.TownyMessaging;
import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.event.NationPreTransactionEvent;
import com.palmergames.bukkit.towny.event.TownPreTransactionEvent;
import com.palmergames.bukkit.towny.event.nation.NationPreTownLeaveEvent;
import com.palmergames.bukkit.towny.event.nation.toggle.NationToggleNeutralEvent;
import com.palmergames.bukkit.towny.event.town.TownLeaveEvent;
import com.palmergames.bukkit.towny.event.town.TownPreSetHomeBlockEvent;
import com.palmergames.bukkit.towny.event.town.TownPreUnclaimCmdEvent;
import com.palmergames.bukkit.towny.exceptions.EconomyException;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.exceptions.TownyException;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.TransactionType;
import com.palmergames.bukkit.towny.object.Translation;
import com.palmergames.bukkit.towny.object.WorldCoord;
import io.github.townyadvanced.flagwar.FlagWar;
import io.github.townyadvanced.flagwar.FlagWarAPI;
import io.github.townyadvanced.flagwar.config.FlagWarConfig;
import io.github.townyadvanced.flagwar.events.CellAttackCanceledEvent;
import io.github.townyadvanced.flagwar.events.CellAttackEvent;
import io.github.townyadvanced.flagwar.objects.CellUnderAttack;
import io.github.townyadvanced.flagwar.events.CellDefendedEvent;
import io.github.townyadvanced.flagwar.events.CellWonEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class FlagWarCustomListener implements Listener {

    private Towny towny;

    public FlagWarCustomListener(FlagWar flagWar) {

		if (flagWar.getServer().getPluginManager().getPlugin("Towny") != null)
		    towny = Towny.getPlugin();

	}

	@EventHandler(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onCellAttackEvent(CellAttackEvent event) {
		if (event.isCancelled())
			return;

		try {
			CellUnderAttack cell = event.getData();
			FlagWar.registerAttack(cell);
		} catch (Exception e) {
			event.setCancelled(true);
			event.setReason(e.getMessage());
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onCellDefendedEvent(CellDefendedEvent event) {

		if (event.isCancelled())
			return;

		Player player = event.getPlayer();
		CellUnderAttack cell = event.getCell().getAttackData();

		try {
			FlagWar.townFlagged(FlagWar.cellToWorldCoord(cell).getTownBlock().getTown());
		} catch (NotRegisteredException ignored) {}

		TownyUniverse universe = TownyUniverse.getInstance();

		WorldCoord worldCoord = new WorldCoord(cell.getWorldName(), cell.getX(), cell.getZ());
		universe.removeWarZone(worldCoord);

		towny.updateCache(worldCoord);

		String playerName;
		if (player == null) {
			playerName = "Greater Forces";
		} else {
			playerName = player.getName();
			Resident playerRes = universe.getResident(player.getUniqueId());
			if (playerRes != null)
				playerName = playerRes.getFormattedName();
		}

		towny.getServer().broadcastMessage(Translation.of("msg_enemy_war_area_defended", playerName, cell.getCellString()));

		// Defender Reward
		// It doesn't entirely matter if the attacker can pay.
		// Also doesn't take into account of paying as much as the attacker can afford (Eg: cost=10 and balance=9).
		if (TownyEconomyHandler.isActive()) {
			try {
				Resident attackingPlayer = universe.getResident(cell.getNameOfFlagOwner());
				Resident defendingPlayer = null;

				// Should never happen
				if (attackingPlayer == null)
					return;

				if (player != null) {
					defendingPlayer = universe.getResident(player.getUniqueId());
				}

				String formattedMoney = TownyEconomyHandler.getFormattedBalance(FlagWarConfig.getDefendedAttackReward());
				if (defendingPlayer == null) {
					if (attackingPlayer.getAccount().deposit(FlagWarConfig.getDefendedAttackReward(), "War - Attack Was Defended (Greater Forces)"))
						try {
							TownyMessaging.sendResidentMessage(attackingPlayer, Translation.of("msg_enemy_war_area_defended_greater_forces", formattedMoney));
						} catch (TownyException ignored) {
						}
				} else {
					if (attackingPlayer.getAccount().payTo(FlagWarConfig.getDefendedAttackReward(), defendingPlayer, "War - Attack Was Defended")) {
						try {
							TownyMessaging.sendResidentMessage(attackingPlayer, Translation.of("msg_enemy_war_area_defended_attacker", defendingPlayer.getFormattedName(), formattedMoney));
						} catch (TownyException ignored) {
						}
						try {
							TownyMessaging.sendResidentMessage(defendingPlayer, Translation.of("msg_enemy_war_area_defended_defender", attackingPlayer.getFormattedName(), formattedMoney));
						} catch (TownyException ignored) {
						}
					}
				}
			} catch (EconomyException e) {
				e.printStackTrace();
			}
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onCellWonEvent(CellWonEvent event) {

		if (event.isCancelled())
			return;

		CellUnderAttack cell = event.getCellAttackData();

		TownyUniverse universe = TownyUniverse.getInstance();
		try {
			Resident attackingResident = universe.getResident(cell.getNameOfFlagOwner());

			// Shouldn't happen
			if (attackingResident == null)
				return;

			Town attackingTown = attackingResident.getTown();
			Nation attackingNation = attackingTown.getNation();

			WorldCoord worldCoord = FlagWar.cellToWorldCoord(cell);
			universe.removeWarZone(worldCoord);

			TownBlock townBlock = worldCoord.getTownBlock();
			Town defendingTown = townBlock.getTown();

			FlagWar.townFlagged(defendingTown);

			// Payments
			double amount = 0;
			String moneyTransferMessage = null;
			if (TownyEconomyHandler.isActive()) {
				try {
					String reasonType;
					if (townBlock.isHomeBlock()) {
						amount = FlagWarConfig.getWonHomeBlockReward();
						reasonType = "Homeblock";
					} else {
						amount = FlagWarConfig.getWonTownBlockReward();
						reasonType = "Townblock";
					}

					if (amount > 0) {
						// Defending Town -> Attacker (Pillage)
						String reason = String.format("War - Won Enemy %s (Pillage)", reasonType);
						amount = Math.min(amount, defendingTown.getAccount().getHoldingBalance());
						defendingTown.getAccount().payTo(amount, attackingResident, reason);

						// Message
						moneyTransferMessage = Translation.of("msg_enemy_war_area_won_pillage", attackingResident.getFormattedName(), TownyEconomyHandler.getFormattedBalance(amount), defendingTown.getFormattedName());
					} else if (amount < 0) {
						// Attacker -> Defending Town (Rebuild cost)
						amount = -amount; // Inverse the amount so it's positive.
						String reason = String.format("War - Won Enemy %s (Rebuild Cost)", reasonType);
						if (!attackingResident.getAccount().payTo(amount, defendingTown, reason)) {
							// Could Not Pay Defending Town the Rebuilding Cost.
							TownyMessaging.sendGlobalMessage(Translation.of("msg_enemy_war_area_won", attackingResident.getFormattedName(), (attackingNation.hasTag() ? attackingNation.getTag() : attackingNation.getFormattedName()), cell.getCellString()));
						}

						// Message
						moneyTransferMessage = Translation.of("msg_enemy_war_area_won_rebuilding", attackingResident.getFormattedName(), TownyEconomyHandler.getFormattedBalance(amount), defendingTown.getFormattedName());
					}
				} catch (EconomyException x) {
					x.printStackTrace();
				}
			}

			// Defender loses townblock
			if (FlagWarConfig.isFlaggedTownBlockTransferred()) {
				// Attacker Claim Automatically
				try {
					townBlock.setTown(attackingTown);
					townBlock.save();
				} catch (Exception te) {
					// Couldn't claim it.
					TownyMessaging.sendErrorMsg(te.getMessage());
					te.printStackTrace();
				}
			} else {

				TownyMessaging.sendPrefixedTownMessage(attackingTown, Translation.of("msg_war_defender_keeps_claims"));
				TownyMessaging.sendPrefixedTownMessage(defendingTown, Translation.of("msg_war_defender_keeps_claims"));
			}

			// Cleanup
			towny.updateCache(worldCoord);

			// Event Message
			TownyMessaging.sendGlobalMessage(Translation.of("msg_enemy_war_area_won", attackingResident.getFormattedName(), (attackingNation.hasTag() ? attackingNation.getTag() : attackingNation.getFormattedName()), cell.getCellString()));

			// Money Transfer message.
			if (TownyEconomyHandler.isActive()) {
				if (amount != 0 && moneyTransferMessage != null) {
					try {
						TownyMessaging.sendResidentMessage(attackingResident, moneyTransferMessage);
					} catch (TownyException ignored) {
					}
					TownyMessaging.sendPrefixedTownMessage(defendingTown, moneyTransferMessage);
				}
			}
		} catch (NotRegisteredException e) {
			e.printStackTrace();
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onCellAttackCanceledEvent(CellAttackCanceledEvent event) {

		if (event.isCancelled())
			return;

		CellUnderAttack cell = event.getCell();

		try {
			FlagWar.townFlagged(FlagWar.cellToWorldCoord(cell).getTownBlock().getTown());
		} catch (NotRegisteredException ignored) {}

		TownyUniverse universe = TownyUniverse.getInstance();

		WorldCoord worldCoord = new WorldCoord(cell.getWorldName(), cell.getX(), cell.getZ());
		universe.removeWarZone(worldCoord);
		towny.updateCache(worldCoord);

		System.out.println(cell.getCellString());
	}


	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    @SuppressWarnings("unused")
    public void onTownLeaveNation(NationPreTownLeaveEvent event) {
		if (FlagWarConfig.isAllowingAttacks()) {
			if (FlagWarAPI.isUnderAttack(event.getTown()) && TownySettings.isFlaggedInteractionTown()) {
				event.setCancelMessage(Translation.of("msg_war_flag_deny_town_under_attack"));
				event.setCancelled(true);
			}

			if (System.currentTimeMillis() - FlagWarAPI.getFlaggedTimestamp(event.getTown()) < TownySettings.timeToWaitAfterFlag()) {
				event.setCancelMessage(Translation.of("msg_war_flag_deny_recently_attacked"));
				event.setCancelled(true);
			}
		}
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    @SuppressWarnings("unused")
    public void onNationWithdraw(NationPreTransactionEvent event) {
		if (FlagWarConfig.isAllowingAttacks() && TownySettings.isFlaggedInteractionNation() && event.getTransaction().getType() == TransactionType.WITHDRAW) {
			for (Town town : event.getNation().getTowns()) {
				if (FlagWarAPI.isUnderAttack(town) || System.currentTimeMillis()- FlagWarAPI.getFlaggedTimestamp(town) < TownySettings.timeToWaitAfterFlag()) {
					event.setCancelMessage(Translation.of("msg_war_flag_deny_nation_under_attack"));
					event.setCancelled(true);
					return;
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    @SuppressWarnings("unused")
    public void onTownWithdraw(TownPreTransactionEvent event) {
		if (FlagWarConfig.isAllowingAttacks() && System.currentTimeMillis() - FlagWarAPI.getFlaggedTimestamp(event.getTown()) < TownySettings.timeToWaitAfterFlag()) {
			event.setCancelMessage(Translation.of("msg_war_flag_deny_recently_attacked"));
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    @SuppressWarnings("unused")
    public void onTownSetHomeBlock(TownPreSetHomeBlockEvent event) {
		if (FlagWarConfig.isAllowingAttacks()) {
			if (FlagWarAPI.isUnderAttack(event.getTown()) && TownySettings.isFlaggedInteractionTown()) {
			    cancelTownPreSetHomeBlockEvent(event, Translation.of("msg_war_flag_deny_town_under_attack"));
				return;
			}

			if (System.currentTimeMillis()- FlagWarAPI.getFlaggedTimestamp(event.getTown()) < TownySettings.timeToWaitAfterFlag()) {
				cancelTownPreSetHomeBlockEvent(event, Translation.of("msg_war_flag_deny_recently_attacked"));
			}

		}
	}

	private void cancelTownPreSetHomeBlockEvent(TownPreSetHomeBlockEvent event, String cancelMessage) {
	    event.setCancelMessage(cancelMessage);
	    event.setCancelled(true);
    }

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    @SuppressWarnings("unused")
    public void onNationToggleNeutral(NationToggleNeutralEvent event) {
		if (FlagWarConfig.isAllowingAttacks()) {
			if (!TownySettings.isDeclaringNeutral() && event.getFutureState()) {
				event.setCancelled(true);
				event.setCancelMessage(Translation.of("msg_err_fight_like_king"));
			} else {
				if (event.getFutureState() && !FlagWarAPI.getCellsUnderAttack().isEmpty())
					for (Resident resident : event.getNation().getResidents())
						FlagWar.removeAttackerFlags(resident.getName());
			}
		}
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    @SuppressWarnings("unused")
    public void onTownLeave(TownLeaveEvent event) {
		if (FlagWarConfig.isAllowingAttacks()) {
			if (FlagWarAPI.isUnderAttack(event.getTown()) && TownySettings.isFlaggedInteractionTown()) {
				event.setCancelled(true);
				event.setCancelMessage(Translation.of("msg_war_flag_deny_town_under_attack"));
				return;
			}

			if (System.currentTimeMillis()- FlagWarAPI.getFlaggedTimestamp(event.getTown()) < TownySettings.timeToWaitAfterFlag()) {
				event.setCancelled(true);
				event.setCancelMessage(Translation.of("msg_war_flag_deny_recently_attacked"));
            }
		}
	}

	@EventHandler (priority= EventPriority.HIGH)
    @SuppressWarnings("unused")
    private void onWarPreUnclaim(TownPreUnclaimCmdEvent event) {
		if (FlagWarAPI.isUnderAttack(event.getTown()) && TownySettings.isFlaggedInteractionTown()) {
			event.setCancelMessage(Translation.of("msg_war_flag_deny_town_under_attack"));
			event.setCancelled(true);
			return; // Return early, no reason to try sequential checks if a town is under attack.
		}

		if (System.currentTimeMillis() - FlagWarAPI.getFlaggedTimestamp(event.getTown()) < TownySettings.timeToWaitAfterFlag()) {
			event.setCancelMessage(Translation.of("msg_war_flag_deny_recently_attacked"));
			event.setCancelled(true);
		}
	}
}