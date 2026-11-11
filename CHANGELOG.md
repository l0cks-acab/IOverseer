# Changelog

## 0.3.0
- Create support: machines that draw stress are listed (press, mixer, millstone, saw, drill, fan and so on), not just ones with an inventory.
- Details panel shows Speed (RPM), network Stress (SU used vs capacity, red when overstressed) and the SU the block itself uses.
- Held tablet preview shows nearby Create SU use with its own graph and a stress bar. With FE machines nearby too, it flips between FE/t and SU every 4 seconds.
- Depots, vaults, chutes, funnels and tunnels are no longer listed.

## 0.2.1
- Renamed to I/Overseer (another mod already uses the name Overseer). The I/O in the name is shown in cyan for Input/Output.

## 0.2.0
- Live preview on the held tablet: power draw (FE/t) with a scrolling graph, stored energy bar and status mix. Toggle with the new Preview button in the tablet screen (saved on the tablet).
- Details panel shows Ars Nouveau Source and Mekanism chemical tanks (gases, infuse types, pigments, slurries).
- Ars machines with no Source show No power.
- Chemical tanks, fluid tanks, source jars and relays are hidden from the list.
- Bigger screen on the tablet texture.

## 0.1.1
- Tested with Mekanism, Powah and AE2.
- Energy cells, cubes, batteries and cables no longer show up as machines.
- AE2 network blocks (drives, controller, interfaces, pattern providers, energy cells) are ignored.
- Output full now detected on machines that block extraction from the null side (Mekanism).
- Alert toast shows the machine name and status on separate lines so it no longer gets cut off.

## 0.1.0
- First release: Overseer Tablet, Overseer Screen, status list, filters, details, ctrl-click in-world highlight (outline, beam, HUD arrow), pins with toast alerts.
