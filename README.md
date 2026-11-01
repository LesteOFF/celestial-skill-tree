# Celestial Skill Tree

A NeoForge mod for **Minecraft Java 1.21.1** that turns your progression into a skill tree generated for each world, presented as a constellation
chart inside **The Astral Observatory**. Buy skill points with vanilla experience, plan a route across the
classes of your world, confirm it in one batch, and reshape your build with the Orb of Reconfiguration.

- 6 classes without other mods — Duelist, Guardian, Arcanist, Marksman, Artificer, Berserker — and a Mage class when
  Iron's Spells 'n Spellbooks, Ars Nouveau or Malum is installed (any one of them alone brings at least six themes and
  keystones of its own). Every class has the same shape: a hub, a straight trunk, themed branches that end in a
  notable, and up to 3 keystones at the tip. Together they form a web of up to 100 skills per class (120 for the Mage,
  which has the most content when every magic mod is present).
- The tree is drawn from a catalog using the world seed, so another world, or another set of mods, gets another tree.
- Every node has a real gameplay effect; keystones trade a strong advantage for a drawback.
- Server-authoritative: the client only previews; the server validates every purchase, allocation and reset.
- English (`en_us`) and Brazilian Portuguese (`pt_br`).

## Requirements

| | Version |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.256 or newer 21.1.x (`[21.1.256,21.2)`) |
| Java | 21 (64-bit) |

Install the jar in `mods/` on **both** the client and the server. No other mods are required; every integration is
optional and used only when its mod is installed.

## License

All Rights Reserved (see [LICENSE](LICENSE)). All art, data, sounds and code were created for this
project; every sound in `assets/celestialskilltree/sounds/` is synthesized. The eclipse, falling star and star shower
events and their sounds are inspired by Nyx by Ellpeck (MIT) and its port Hyxcate; no code or sound is copied from
them. The skill tree itself is inspired by Passive Skill Tree by Daripher.
