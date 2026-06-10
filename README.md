# AOHPAgentDriver

**[中文说明](README.zh-CN.md)**

AOHPAgentDriver is a system-level priv-app on AOHP (Android Open Harness Project). Its role is to expose AOHP OS system capabilities to agents through a single local JSON-RPC WebSocket service.

## Role in the system

```mermaid
flowchart LR
    subgraph Clients
        OC[OpenClaw agent]
        CLI[aohp CLI]
    end

    subgraph AOHP["AOHP OS (AOSP)"]
        AD[AOHPAgentDriver<br/>priv-app]
        subgraph Capabilities
            PBI[Parallel Background Interaction]
            AUI[Agent-aware UI Enhancement]
            NSR[Native Sandbox Runtime]
            UFS[Unified File Shortcut]
            ESA[Event Stream Abstraction]
            UDA[User Defined Apps]
        end
        FW[Framework & system services]
    end

    OC -->|skills| CLI
    CLI -->|WebSocket| AD
    AD --> PBI & AUI & NSR & UFS & ESA & UDA
    AD --> FW
```



