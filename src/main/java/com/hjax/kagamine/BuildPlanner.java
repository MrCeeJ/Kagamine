package com.hjax.kagamine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.UnitType;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.CloakState;
import com.hjax.kagamine.UnitControllers.Drone;
import com.hjax.kagamine.UnitControllers.Larva;

import javafx.util.Pair;

public class BuildPlanner {
	public static boolean is_all_in = false;
	public static boolean pulled_off_gas = false;
	
	public static void on_frame() {
		if (!build_completed()) execute_build();
		else {
			//if (!is_all_in && count(Units.ZERG_DRONE) < 30 && Wisdom.cannon_rush()) do_ravager_all_in();
			//if (is_all_in && Game.supply() > 70 && Game.get_opponent_race() == Race.TERRAN) hunter_killer();
			if (Game.get_opponent_race() == Race.PROTOSS) {
				if (GameInfoCache.count_enemy(Units.PROTOSS_CARRIER) > 0 ||
					GameInfoCache.count_enemy(Units.PROTOSS_VOIDRAY) > 0 || 
					GameInfoCache.count_enemy(Units.PROTOSS_TEMPEST) > 0 ||
					GameInfoCache.count_enemy(Units.PROTOSS_MOTHERSHIP) > 0 ||
					GameInfoCache.count_enemy(Units.PROTOSS_STARGATE) > 0) {
					if (Build.composition.contains(Units.ZERG_ROACH)) {
						Build.composition = Arrays.asList(Units.ZERG_HYDRALISK, Units.ZERG_ZERGLING);
						Build.ideal_workers = 70;
						Build.max_queens = 4;
					}
				}
			}
			if (Game.supply_cap() < 200) {
				float larva = BaseManager.larva_rate();
				if (should_build_queens()) larva += 2;
				if (Game.supply_cap() + (GameInfoCache.in_progress(Units.ZERG_OVERLORD) * 8) - Game.supply() <= larva * 1.5) {
					if (Game.can_afford(Units.ZERG_OVERLORD)) {
						if (Larva.has_larva()) {
							Game.purchase(Units.ZERG_OVERLORD);
							Larva.produce_unit(Units.ZERG_OVERLORD);
						}
					} else return;
				}
			}
			
			// TODO make this less of a hack
			if (GameInfoCache.count_friendly(Units.ZERG_SPAWNING_POOL) > 0 && !(GameInfoCache.get_units(Alliance.SELF, Units.ZERG_SPAWNING_POOL).get(0).unit().getOrders().size() == 0) && Build.pull_off_gas) {
				pulled_off_gas = true;
				for (UnitInPool drone: GameInfoCache.get_units(Alliance.SELF, Units.ZERG_DRONE)) {
					if (!(drone.unit().getOrders().size() == 0) && 
							drone.unit().getOrders().get(0).getAbility() == Abilities.HARVEST_GATHER && 
							Game.get_unit((drone.unit().getOrders().get(0).getTargetedUnitTag()).get()).unit().getType() == Units.ZERG_EXTRACTOR) {
						Game.unit_command(drone, Abilities.STOP);
					}
				}
			}
			if (count(Units.ZERG_DRONE) > 40 && pulled_off_gas) {
				pulled_off_gas = false;
				Build.pull_off_gas = false;
			}
			if (Game.army_supply() < 30 && BaseManager.base_count() < 3) {
				if (count(Units.ZERG_SPINE_CRAWLER) < 3 && !Wisdom.cannon_rush() && Build.scout) {
					if (GameInfoCache.count_friendly(Units.ZERG_SPAWNING_POOL) > 0 && (BaseManager.base_count() > 1 || Wisdom.proxy_detected())) {
						if (Wisdom.all_in_detected() || Wisdom.proxy_detected()) {
							if (!Game.can_afford(Units.ZERG_SPINE_CRAWLER)) return;
							Game.purchase(Units.ZERG_SPINE_CRAWLER);
							BaseManager.build(Units.ZERG_SPINE_CRAWLER);
						}
					}
				}
			}
			
			if ((!Wisdom.all_in_detected() || Game.army_supply() > 15 || Game.minerals() > 700) && GameInfoCache.in_progress(Units.ZERG_HATCHERY) == 0 && should_expand()) {
				if ((count(Units.ZERG_DRONE) > ((BaseManager.base_count() - 1) * 23) && BaseManager.base_count() < 4) || Game.army_supply() > 100) {
					if (!Game.can_afford(Units.ZERG_HATCHERY)) {
						if (!BaseManager.get_next_base().has_walking_drone() && Game.minerals() > 100) {
							UnitInPool drone = BaseManager.get_free_worker(BaseManager.get_next_base().location);
							if (drone != null) {
								BaseManager.get_next_base().set_walking_drone(drone);
							}
						}
						return;
					} else {
						BaseManager.build(Units.ZERG_HATCHERY);
						Game.purchase(Units.ZERG_HATCHERY);
					}
				}
			}
			
			if (count(Units.ZERG_DRONE) >= 35) {
				boolean needs_spores = Wisdom.air_detected();
				for (UnitInPool u: GameInfoCache.get_units(Alliance.ENEMY)) {
					if (u.unit().getCloakState().orElse(CloakState.NOT_CLOAKED) == CloakState.CLOAKED_DETECTED || u.unit().getFlying().orElse(false)) {
						if (u.unit().getType() != Units.ZERG_OVERLORD && 
							u.unit().getType() != Units.ZERG_OVERSEER &&
							u.unit().getType() != Units.TERRAN_MEDIVAC &&
							u.unit().getType() != Units.PROTOSS_OBSERVER) {
							needs_spores = true;
						}
					}
				}
				if (needs_spores) {
					// TODO this returns even if all of our bases have spores
					if (!Game.can_afford(Units.ZERG_SPORE_CRAWLER)) return;
					BaseManager.build_defensive_spores();
				}
			}
			
			if (Game.minerals() > 50 && Game.gas() > 50 && GameInfoCache.count_friendly(Units.ZERG_LAIR) > 0 && count(Units.ZERG_OVERLORD) + count(Units.ZERG_OVERLORD_COCOON) < 2) {
				for (UnitInPool ovie: GameInfoCache.get_units(Alliance.SELF, Units.ZERG_OVERLORD)) {
					Game.spend(50, 50);
					Game.unit_command(ovie, Abilities.MORPH_OVERSEER);
					break;
				}
			}
			
			TechManager.on_frame();
			
			if (!pulled_off_gas && (!is_all_in || count(Units.ZERG_DRONE) > 30 || (Wisdom.all_in_detected() && count(Units.ZERG_DRONE) > 25))) {
				for (UnitType u: Build.composition) {
					if (Balance.has_tech_requirement(u)) {
						if (!(count(Balance.next_tech_requirement(u)) > 0)) {
							if (Balance.next_tech_requirement(u) == Units.ZERG_LAIR) {
								if (BaseManager.base_count() >= 3 && count(Units.ZERG_DRONE) > 30) {
									if (Game.minerals() < 150 && Game.gas() > 100) return;
									if (Game.can_afford(Balance.next_tech_requirement(u))) {
										for (Base b: BaseManager.bases) {
											if (b.has_command_structure() && b.command_structure.unit().getBuildProgress() > 0.999 && b.command_structure.unit().getOrders().size() == 0) {
												Game.unit_command(b.command_structure, Abilities.MORPH_LAIR);
												break;
											}
										}
									}
								}
							} else {
								if (Game.can_afford(Balance.next_tech_requirement(u))) {
									Game.purchase(Balance.next_tech_requirement(u));
									BaseManager.build(Balance.next_tech_requirement(u));
								} else if (BaseManager.active_extractors() > 2) return;
							}
						}
					}
				}
			}
			
			if (should_build_queens()) {
				if (Game.supply_cap() - Game.supply() >= 2) {
					if (Game.can_afford(Units.ZERG_QUEEN)) {
						// TODO lairs and hives cant make queens
						for (UnitInPool u: GameInfoCache.get_units(Alliance.SELF, Units.ZERG_HATCHERY)) {
							if (u.unit().getOrders().size() == 0) {
								Game.purchase(Units.ZERG_QUEEN);
								Game.unit_command(u, Abilities.TRAIN_QUEEN);
							}
						}
					} else if (count(Units.ZERG_QUEEN) < BaseManager.base_count()) {
						return;
					}
				}
			}
			
			if (should_build_army() || !should_build_drones()) {
				if (Game.minerals() > 25 && Game.gas() > 75 && GameInfoCache.count_friendly(Units.ZERG_ROACH) > 0 && Build.composition.contains(Units.ZERG_RAVAGER)) {
					for (UnitInPool u: GameInfoCache.get_units(Alliance.SELF, Units.ZERG_ROACH)) {
						Game.unit_command(u, Abilities.MORPH_RAVAGER);
						Game.spend(25, 75);
						break;
					}
				}
				if (Game.minerals() > 25 && Game.gas() > 25 && GameInfoCache.count_friendly(Units.ZERG_ZERGLING) > 0 && Build.composition.contains(Units.ZERG_BANELING)) {
					for (UnitInPool u: GameInfoCache.get_units(Alliance.SELF, Units.ZERG_ZERGLING)) {
						Game.unit_command(u, Abilities.TRAIN_BANELING);
						Game.spend(25, 25);
						break;
					}
				}
				if (Larva.has_larva() && Game.can_afford(next_army_unit())) {
					if (next_army_unit() != Units.INVALID) {
						Game.purchase(next_army_unit());
						Larva.produce_unit(next_army_unit());
					}
				}
			}
			else if (should_build_drones()) {
				if (Game.can_afford(Units.ZERG_DRONE) && Larva.has_larva()) {
					Game.purchase(Units.ZERG_DRONE);
					Larva.produce_unit(Units.ZERG_DRONE);
				}
			}
			if (count(Units.ZERG_EXTRACTOR) >= 3) {
				if (count(Units.ZERG_EVOLUTION_CHAMBER) < 2 && count(Units.ZERG_DRONE) > 35 && BaseManager.base_count() > 3) {
					if (Game.can_afford(Units.ZERG_EVOLUTION_CHAMBER)) {
						BaseManager.build(Units.ZERG_EVOLUTION_CHAMBER);
						Game.purchase(Units.ZERG_EVOLUTION_CHAMBER);
					}
				}
			}
			if (count(Units.ZERG_DRONE) > 45 || count(Units.ZERG_EXTRACTOR) == 0) {
				if (((BaseManager.active_extractors() | GameInfoCache.in_progress(Units.ZERG_EXTRACTOR)) < ((count(Units.ZERG_DRONE) - 15) / 8)) && Game.can_afford(Units.ZERG_EXTRACTOR)) {
					BaseManager.build(Units.ZERG_EXTRACTOR);
					Game.purchase(Units.ZERG_EXTRACTOR);
				}
			}
		}
	}
	
	public static void execute_build() {
		if (Build.build.get(Build.build_index).getKey() <= Game.supply()) {
			if (Build.build.get(Build.build_index).getValue() == Units.ZERG_HATCHERY && !(BaseManager.get_next_base().has_walking_drone()) && Game.minerals() > 150) {
				for (UnitInPool u: GameInfoCache.get_units(Alliance.SELF, Units.ZERG_DRONE)) {
					if (Drone.can_build(u)) {
						BaseManager.get_next_base().set_walking_drone(u);
						return;
					}
				}
			}
			if (Game.can_afford(Build.build.get(Build.build_index).getValue())) {
				if (Game.is_structure(Build.build.get(Build.build_index).getValue())) {
					BaseManager.build(Build.build.get(Build.build_index).getValue());
					Game.purchase(Build.build.get(Build.build_index).getValue());
					Build.build_index++;
				} else {
					if (Larva.has_larva()) {
						Game.purchase(Build.build.get(Build.build_index).getValue());
						Larva.produce_unit(Build.build.get(Build.build_index).getValue());
						Build.build_index++;
					}
				}
			}
			return;
		}
		if (Larva.has_larva() && Game.can_afford(Units.ZERG_DRONE)) {
			Game.purchase(Units.ZERG_DRONE);
			Larva.produce_unit(Units.ZERG_DRONE);
		}
	}
	
	public static boolean build_completed() {
		return Build.build_index >= Build.build.size();
	}
	
	public static int count(UnitType u) {
		return GameInfoCache.count_friendly(u) + GameInfoCache.in_progress(u);
	}
	
	public static UnitType next_army_unit() {
		UnitType best = Units.INVALID;
		for (UnitType u: Build.composition) {
			if (u == Units.ZERG_BANELING) continue;
			if (GameInfoCache.count_friendly(Balance.get_tech_structure(u)) > 0) {
				if (best == Units.INVALID) best = u;
				if (Game.get_unit_type_data().get(u).getVespeneCost().orElse(0) < Game.gas()) {
					if (Game.get_unit_type_data().get(u).getVespeneCost().orElse(0) > Game.get_unit_type_data().get(best).getVespeneCost().orElse(0)) {
						best = u;
					}
				}
			}
		}
		return best;
	}
	
	public static boolean should_build_queens() {
		if (GameInfoCache.count_friendly(Units.ZERG_SPAWNING_POOL) == 0) return false;
		int queen_target = 0;
		if (Build.max_queens == -1) {
			if (BaseManager.base_count() < 3) {
				queen_target = BaseManager.base_count();
			} else {
				queen_target = Math.min(BaseManager.base_count() + 4, 6);
			}
		} else {
			queen_target = Build.max_queens;
		}
		if (Wisdom.proxy_detected()) {
			queen_target = 1;
			if (Game.minerals() > 400) {
				queen_target += 1;
			}
		}
		if (BaseManager.base_count() < 3 && count(Units.ZERG_QUEEN) < queen_target || (BaseManager.base_count() >= 3 && (count(Units.ZERG_QUEEN) < queen_target)) && GameInfoCache.count_friendly(Units.ZERG_SPAWNING_POOL) > 0) {
			return true;
		}
		return false;
	}
	
	public static boolean should_build_army() {
		int target = 2 + 2 * count(Units.ZERG_QUEEN);
		if (Game.get_opponent_race() == Race.ZERG) target = 10;
		if (Wisdom.all_in_detected()) target = 10;
		if (Wisdom.proxy_detected()) target = 20;
		if (Game.army_supply() < target || (ThreatManager.under_attack() || Game.army_supply() < 4 * ThreatManager.seen.size())) {
			if (next_army_unit() != Units.INVALID) {
				return true;
			}
		}
		if (Wisdom.play_safe() && Game.army_supply() * 1.5 < count(Units.ZERG_DRONE) && count(Units.ZERG_DRONE) > 43) return true;
		if (Game.army_supply() * 2 < count(Units.ZERG_DRONE) && count(Units.ZERG_DRONE) > 45) return true;
		if (Game.get_opponent_race() == Race.PROTOSS && count(Units.ZERG_DRONE) > 45 && Game.army_supply() < 45) return true;
		if (Wisdom.all_in_detected() && Game.army_supply() < count(Units.ZERG_DRONE) && count(Units.ZERG_DRONE) > 30) return true;
		if (count(Units.ZERG_DRONE) > 50 && Game.gas() > 100 && next_army_unit() == Units.ZERG_MUTALISK && count(Units.ZERG_MUTALISK) < 10) return true;
		return count(Units.ZERG_DRONE) >= worker_cap();
	}
	
	public static boolean should_build_drones() {
		boolean should_drone = false;
		for (Base b: BaseManager.bases) {
			if (b.has_command_structure() && ((b.command_structure.unit().getIdealHarvesters().orElse(0) - b.command_structure.unit().getAssignedHarvesters().orElse(0)) > 0 || (b.command_structure.unit().getBuildProgress() > 0.8))) {
				should_drone = true;
				break;
			}
		}
		if (!should_drone) {
			return false;
		}
		return (count(Units.ZERG_DRONE) < worker_cap());
	}
	
	public static int worker_cap() {
		int drone_target = 70;
		return Math.min(drone_target, Build.ideal_workers);
	}
	
	public static boolean should_expand() {
		return (BaseManager.base_count() < Build.ideal_hatches) || (Build.ideal_hatches == -1);
	}
	
	public static void decide_build() {
		switch (Game.get_opponent_race()) {
			case PROTOSS:
				Build.build = new ArrayList<>(Arrays.asList(new Pair<Integer, UnitType>(13, Units.ZERG_OVERLORD),
															new Pair<Integer, UnitType>(17, Units.ZERG_HATCHERY),
															new Pair<Integer, UnitType>(17, Units.ZERG_EXTRACTOR),
															new Pair<Integer, UnitType>(17, Units.ZERG_SPAWNING_POOL)));
				Build.composition = Arrays.asList(Units.ZERG_ZERGLING, Units.ZERG_ROACH, Units.ZERG_HYDRALISK);
				Build.ideal_hatches = -1;
				Build.scout = true;
				Build.push_supply = 185;
				Build.ideal_workers = 70;
				Build.upgrades = new HashSet<>(Arrays.asList(Upgrades.ZERGLING_MOVEMENT_SPEED, Upgrades.EVOLVE_MUSCULAR_AUGMENTS, Upgrades.ZERG_GROUND_ARMORS_LEVEL1, Upgrades.ZERG_GROUND_ARMORS_LEVEL2, Upgrades.ZERG_MISSILE_WEAPONS_LEVEL1, Upgrades.ZERG_MISSILE_WEAPONS_LEVEL2));
				break;
			case ZERG:
				Build.build = new ArrayList<>(Arrays.asList(new Pair<Integer, UnitType>(14, Units.ZERG_HATCHERY),
						new Pair<Integer, UnitType>(14, Units.ZERG_EXTRACTOR),
						new Pair<Integer, UnitType>(14, Units.ZERG_SPAWNING_POOL),
						new Pair<Integer, UnitType>(14, Units.ZERG_OVERLORD)));
				Build.composition = Arrays.asList(Units.ZERG_ZERGLING);
				Build.ideal_hatches = 2;
				Build.scout = false;
				Build.push_supply = 30;
				Build.ideal_workers = 16;
				Build.max_queens = 1;
				Build.upgrades = new HashSet<>(Arrays.asList(Upgrades.ZERGLING_MOVEMENT_SPEED));
				break;			
			case TERRAN:
				Build.build = new ArrayList<>(Arrays.asList(new Pair<Integer, UnitType>(13, Units.ZERG_OVERLORD),
						new Pair<Integer, UnitType>(17, Units.ZERG_HATCHERY),
						new Pair<Integer, UnitType>(17, Units.ZERG_EXTRACTOR),
						new Pair<Integer, UnitType>(17, Units.ZERG_SPAWNING_POOL)));
				Build.composition = Arrays.asList(Units.ZERG_ZERGLING, Units.ZERG_MUTALISK);
				Build.ideal_hatches = -1;
				Build.scout = true;
				Build.push_supply = 190;
				Build.ideal_workers = 70;
				Build.pull_off_gas = true;
				Build.upgrades = new HashSet<>(Arrays.asList(Upgrades.ZERGLING_MOVEMENT_SPEED, Upgrades.EVOLVE_MUSCULAR_AUGMENTS, Upgrades.ZERG_GROUND_ARMORS_LEVEL1, Upgrades.ZERG_GROUND_ARMORS_LEVEL2, Upgrades.ZERG_FLYER_WEAPONS_LEVEL1, Upgrades.ZERG_FLYER_WEAPONS_LEVEL2));
				break;
			default:
				
		}
	}
}
