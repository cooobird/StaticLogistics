# StaticLogistics

### 支持的模组
- **Mekanism**：化学品和热量传输
- **Ars Nouveau**：魔源传输
- **PneumaticCraft: Repressurized**：气压和热量传输
- **Create**：额外兼容性
- **FTB Teams**：基于团队的权限

### 集成设置
集成特定的堆叠大小和倍率可以在服务器配置文件中配置。

## 命令

### `/sl info <pos>`
显示特定位置的物流信息。

### `/sl list [page]`
列出所有活跃的物流分组。

### `/sl strategies [page]`
列出数据组件匹配策略。

### `/sl transfer <player> <group_id>`
将链接从一个玩家转移到另一个玩家。

### `/sl rename <group_id> <new_name>`
重命名物流分组。

### `/sl cleanup <player>`
清理玩家拥有的所有链接。

## 性能优化建议

1. **调整批处理大小**：如果遇到卡顿，减少 `ticker_batch_size`
2. **优化缓存**：在大服务器上增加缓存大小以获得更好的性能
3. **网络设置**：如果有网络问题，降低 `max_bulk_entries`
4. **清理链接**：使用清理命令删除未使用的链接

## 故障排除

### 链接不工作？
- 检查两个节点是否具有相同的频道
- 验证两个节点是否在同一维度（除非使用维度升级）
- 确保传输类型匹配（物品/流体/能量）

### 内存占用过高？
- 在配置中减少缓存大小
- 使用清理命令删除未使用的链接

### 网络问题？
- 在网络设置中降低 `max_bulk_entries`
- 检查服务器的网络配置

## 许可证

本模组采用 GNU LGPL 3.0 许可证。

## 致谢

- **作者**：cooobird, WangXiaoJin
- **贡献者**：感谢所有为该项目做出贡献的人

## 支持

如有问题、建议或疑问，请访问项目的问题跟踪器。

## 更新日志

详见 [changelog.md](changelog.md) 获取详细的版本历史。
