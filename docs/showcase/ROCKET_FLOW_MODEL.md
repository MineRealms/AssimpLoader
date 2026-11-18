# Static Rocket Flow Model

This document describes the reduced-order model used by the stationary rocket test stand. The state is calculated in Java in SI units, then sampled by the client renderer. It is intended to make mass flow, exit conditions, momentum, and nozzle thermal lag move together coherently. It is not a combustion solver or CFD.

## Inputs

ExhaustProfile keeps the existing gas, atmosphere, and appearance sections. An optional flow section adds engine and ambient assumptions. Old profiles without flow continue to load with bounded defaults derived from gamma and R.

    {
      "gas": {
        "exit_pressure_pa": 180000,
        "exit_temperature_k": 2400,
        "exit_mach": 2.2,
        "gamma": 1.22,
        "specific_gas_constant": 355
      },
      "flow": {
        "specific_heat_j_per_kg_k": 1959.5,
        "ambient_temperature_k": 288.15,
        "wall_areal_heat_capacity_j_per_m2_k": 125000,
        "wall_heat_transfer_w_per_m2_k": 4000,
        "wall_emissivity": 0.72,
        "mixing_coefficient": 0.35,
        "soot_fraction": 0.002,
        "water_fraction": 0.18,
        "gravity_mps2": 9.81,
        "wind_x_mps": 0,
        "wind_z_mps": 0,
        "gas_refractivity_m3_per_kg": 0.00023,
        "maximum_flow_ratio": 1.5
      }
    }

All pressures are Pa, temperatures K, mass fractions dimensionless, heat capacity per area J/(m2 K), heat transfer coefficient W/(m2 K), wind m/s, gravity m/s2, and gas refractivity m3/kg. specific_heat_j_per_kg_k is checked against gamma*R/(gamma-1); a profile with substantially inconsistent ideal-gas values is rejected. Soot and water mass fractions must sum to at most one. Numeric fields are finite and range checked.

The nozzle exit radius is supplied by the model attachment in blocks. The atmosphere's metres_per_block converts it to metres. altitudeDependent optionally applies a bounded exponential pressure approximation using scaleHeightMetres; ambient temperature remains the explicitly configured constant.

## Design Point

The configured exit Mach number and specific heat ratio define the ideal isentropic area ratio:

    epsilon = Ae / At
            = (1/M) * [2/(gamma+1) * (1 + (gamma-1)*M^2/2)]
              ^ ((gamma+1)/(2*(gamma-1)))

The exit area is Ae = pi * (radiusBlocks * metresPerBlock)^2; the throat area is At = Ae/epsilon. This uses the configured design Mach to infer the throat geometry, rather than treating exit Mach and throat area as independent measurements.

The design chamber stagnation state and choked mass flow are:

    T0 = Te * (1 + (gamma-1)*Me^2/2)
    p0 = pe * (T0/Te)^(gamma/(gamma-1))
    mdot_nominal = At*p0*sqrt(gamma/(R*T0))
                   * (2/(gamma+1))^((gamma+1)/(2*(gamma-1)))

At the design point this agrees with exit continuity, mdot = rho_e * ve * Ae, where rho_e = pe/(R*Te) and ve = Me*sqrt(gamma*R*Te). The profile values are therefore a self-consistent idealized design point. They are not claimed to be measured engine data.

## Time-Varying State

The stand accepts a target mass flow in kg/s. A negative block-entity setting (-1) selects the profile-derived nominal value. Explicit requests are clamped to maximum_flow_ratio * nominalMassFlow. Burning off sets the target to zero. The flow response follows a first-order chamber/valve approximation:

    mdot(t + dt) = mdot(t) + (target - mdot(t)) * (1 - exp(-dt/tau))

tau is the existing appearance.response_seconds. The exact exponential update makes mass-flow response independent of how a fixed elapsed time is divided among game ticks. This is an effective pressure-response model; it does not integrate chamber inventory, propellant feed dynamics, or combustion chemistry.

The low-flow startup/shutdown branch solves a subsonic exit Mach using the commanded mass flow and ambient exit pressure. Between that branch and nominal flow, a smooth bounded interpolation approaches the configured design Mach. The transient is deliberately stable and continuous, but it does not locate a moving normal shock inside the nozzle. Supersonic exit properties during this blend must be treated as approximate.

For nonzero flow, the exported state preserves continuity and ideal-gas closure:

    Te(t) = T0(t) / (1 + (gamma-1)*Me(t)^2/2)
    ve(t) = Me(t) * sqrt(gamma*R*Te(t))
    rho_e(t) = mdot(t) / (Ae*ve(t))
    pe(t) = rho_e(t) * R * Te(t)

T0(t) approaches the design chamber temperature with the square root of normalized flow, and approaches ambient temperature at zero flow. At nominal design flow the output matches the configured exit pressure and temperature. Pressure thrust and momentum thrust are combined as:

    F = mdot*ve + (pe - pamb)*Ae

The signed pressure term is retained; an overexpanded estimate can reduce net thrust. At exactly zero flow, jet thrust, exit density, velocity, and exhaust power are zero.

exhaustEnergyWatts is the flow's sensible enthalpy rise plus kinetic-energy rate:

    P = mdot * (cp * max(Te - Tamb, 0) + ve^2/2)

It is a bounded rendering/diagnostic quantity, not chemical combustion power.

## Nozzle Wall Temperature

The nozzle wall has a separate temperature state and does not instantly cool when the flow stops. The profile defines areal heat capacity C_A, gas-side heat transfer coefficient h0, and emissivity epsilon. The model linearizes radiative exchange around the current wall temperature:

    h_flow = h0 * load^0.8
    h_rad = epsilon * sigma * (Tw + Tamb) * (Tw^2 + Tamb^2)
    T_eq = (h_flow*Tgas + h_rad*Tamb) / (h_flow + h_rad)
    Tw_next = T_eq + (Tw - T_eq) * exp(-dt*(h_flow+h_rad)/C_A)

The gas-side target temperature approaches the design chamber temperature with load. Radiative cooling remains active after shutdown. This is a lumped surface-temperature approximation; it does not model metal layers, conduction into the engine, spatial temperature gradients, or spectral emission. The renderer should use wall temperature as a bounded thermal-state input, not infer a physically exact RGB color from it.

The wall ODE uses internal steps no larger than 0.01 seconds and samples the analytic mass-flow response at each substep midpoint. This keeps the documented 50 ms game tick and 10 ms test tick partitions on the same thermal trajectory while retaining the nonlinear radiative term.

## Plume Inputs and Limits

mixingCoefficient, sootFraction, waterFraction, gasRefractivityM3Kg, ambient temperature, wind, and gravity are explicit bounded inputs supplied to the client plume transport. They are not inferred from the gas temperature. In particular, soot is not an automatic consequence of hot exhaust, and water condensation requires ambient humidity/saturation information that this model does not calculate. The renderer's smoke and refraction are visual approximations driven by these inputs.

Mach-diamond visibility is meaningful only for a supersonic jet with exit pressure different from ambient pressure. The Java helper gates its estimate near Mach one and suppresses it for pressure-matched flow. The spacing/contrast is an empirical visual estimate, not a compressible-flow shock-cell solution. Strongly overexpanded separation, underexpanded Mach disks, shock motion through a nozzle, boundary layers, viscous loss, reacting species, and atmospheric humidity are not solved.

Gravity and wind are relevant to the slower entrained plume and residual smoke, not to bending the near-nozzle high-speed jet as though it were a projectile. Ground contact, smoke history, and pixel-level volumetric integration are client-rendering responsibilities; the flow state itself remains attached to a stationary nozzle.

## State API

RocketEngineState.tick(profile, nozzleRadiusBlocks, altitudeBlocks, targetMassFlowKgS, dtSeconds) advances the bounded state. interpolate(partialTick) returns an immutable ExhaustFlowState for rendering. Its core fields include actual and target mass flow, exit velocity/temperature/pressure/density, ambient pressure, nozzle wall temperature, thrust, throttle, exhaust energy, mixture fractions, and transport inputs.

ShowcaseBlockEntity.getTargetMassFlowKgS() returns -1 for automatic nominal flow or the saved override in kg/s. setTargetMassFlowKgS(-1) restores automatic mode; setTargetMassFlowKgS(0) requests a shutdown while retaining the selected profile. The flow setting is saved and synchronized with the existing burning/profile controls.

## Invariants Covered by Focused Tests

- Inferred throat area and nominal flow match the ideal design exit state.
- For positive flow, rho_e * ve * Ae equals actual mass flow and pe = rho_e * R * Te.
- Zero flow has zero jet thrust, jet energy, and density.
- Pressure-matched exit conditions do not invent Mach diamonds.
- Startup response is time-based; nozzle wall temperature rises and cools with thermal lag.
- Interpolated values remain finite, and invalid flow, radius, timestep, and mixture inputs are rejected.
