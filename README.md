# L-DINF Simulator

A Java-based simulator for the **L-DINF** (Logic of "Inferable") epistemic framework. It models how cooperative, resource-limited agents reason, act, and interact within and across groups.

The simulator supports:

- **Knowledge-based reasoning** — agents derive new beliefs from inference rules
- **Physical and mental budgets** — every action has a cost; agents share mental costs within the group
- **Preference-driven action selection** — agents choose among equivalent actions based on preference values
- **Role-based enabling** — each agent is restricted to a set of allowed physical actions
- **Inter-group agent lending** — when a group is stalled, it can borrow an agent from another group to perform a missing action

---

## `.ldinf` File Syntax

The entire scenario is defined in a single `.ldinf` file using nested `GROUP` / `AGENT` blocks. Comments start with `#`.

### Directives Reference

| Directive | Scope | Description |
|-----------|-------|-------------|
| `B1: <n>` | Group | Total mental budget (energy), split equally among agents |
| `B2: <res:n, ...>` | Group | Physical budget (resource amounts) |
| `Ki: <premises> ---> <conclusion>` | Group | Inference rule in the shared knowledge base |
| `Cl: <abstract> = <concrete>` | Group | Equivalence class (abstract action ↔ concrete action) |
| `C1: <n>` | Group | Base cost of a mental inference step |
| `C2: <action> [res:n, ...]` | Group | Cost of a physical action (resource tuple) |
| `P: <action> = <n>` | Group | Preference value for a physical action |
| `G: <atom>` | Group | Common group goal |
| `LENDING: enabled\|disabled` | Group | Enable/disable inter-group agent lending |
| `Bi: <atom>` | Agent | Initial fact in the agent's working memory |
| `Hi: <action>` | Agent | Physical action the agent is enabled to perform |
| `Gi: <atom>` | Agent | Personal goal of the agent |

### Minimal Example

```
GROUP team_alpha {
    B1: 50
    B2: wood:10

    Ki: has(wood) ---> done(build_shelter)
    C1: 1
    C2: build_shelter [ENERGY:5, wood:3]
    P: build_shelter = 8
    G: done(build_shelter)
    LENDING: disabled

    AGENT worker_1 {
        Bi: has(wood)
        Hi: build_shelter
        Gi: done(build_shelter)
    }
}
```

### Multi-Group Example with Lending

```
GROUP cooks {
    B1: 100
    B2: ingredients:50, gas:20

    Ki: has(ingredients) & has(oven) ---> done(cook_main_dish)
    Cl: cook_main_dish = cook_steak
    Cl: cook_main_dish = cook_pasta
    C1: 2
    C2: cook_steak [ENERGY:8, ingredients:5, gas:3]
    C2: cook_pasta [ENERGY:5, ingredients:2, gas:2]
    P: cook_steak = 10
    P: cook_pasta = 5
    G: done(cook_main_dish)
    LENDING: enabled

    AGENT chef {
        Bi: has(ingredients)
        Bi: has(oven)
        Hi: cook_steak
        Hi: cook_pasta
        Gi: done(cook_main_dish)
    }
}

GROUP organizers {
    B1: 60
    B2: decorations:10, venue:1

    Ki: has(decorations) & has(venue) ---> done(decorate_venue)
    Ki: done(decorate_venue) & done(cook_main_dish) ---> done(organize_event)
    Cl: cook_main_dish = cook_steak
    Cl: cook_main_dish = cook_pasta
    C1: 2
    C2: decorate_venue [ENERGY:3, decorations:1, venue:0]
    P: decorate_venue = 8
    G: done(organize_event)
    LENDING: enabled

    AGENT planner {
        Bi: has(decorations)
        Bi: has(venue)
        Hi: decorate_venue
        Gi: done(decorate_venue)
    }
}
```

In this example the `organizers` group cannot cook, so the simulator detects a stall and borrows the `chef` agent from the `cooks` group to fulfill the missing sub-goal.

---

## Running the Simulator

### Prerequisites

- **Java 21** (or later) — [download from Adoptium](https://adoptium.net/) or [Oracle](https://www.oracle.com/java/technologies/downloads/)

Verify your installation:

```bash
java -version
```

### Run an Example

```bash
java -jar L-DINF_simulator.jar .\examples\banquet.ldinf
```

### Included Examples

| File | Description |
|------|-------------|
| `examples/banquet.ldinf` | Two groups, lending enabled — full banquet + event scenario |
| `examples/case_study_A.ldinf` | Self-sufficient groups, lending disabled |
| `examples/case_study_B.ldinf` | Cross-group dependency, lending enabled |

Run any of them:

```bash
java -jar L-DINF_simulator.jar .\examples\case_study_A.ldinf
java -jar L-DINF_simulator.jar .\examples\case_study_B.ldinf
```
