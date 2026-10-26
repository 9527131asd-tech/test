# LLM“学习”机制教学实验室

这是可运行的机制教学项目：7个演示对应15篇论文，包含代码、实验数据、中文算法说明和实现边界。项目不是15篇论文的完整复现。默认不调用LLM/API，使用Python 3.10+标准库，无需GPU或第三方依赖。

## 快速运行

```bash
python lab.py all --out results
python -m unittest -v
python experiment.py
```

单项演示：`coordination`、`planning`、`revision`、`search`、`supervised`、`reward`、`memory`。

## 记忆原型

```bash
python lab.py remember --key favorite_tea --value green
python lab.py remember --key favorite_tea --value black
python lab.py recall --query favorite_tea --budget 20
```

完整事件历史保存在SQLite，检索返回与查询匹配且适合预算的最新事实。演示采用显式key/value；代理计数单位不是模型计费token。自由文本抽取和真实LLM token测量尚未实现。

## 项目结构

- `lab.py`：机制教学演示和持久记忆原型。
- `experiment.py`：30个随机种子的合成实验。
- `test_lab.py`：梯度、参数训练、搜索和记忆检查。
- `papers.json`、`论文算法对照表.csv`：15篇论文和实验模块映射。
- `算法说明.md`：中文算法解释。
- `实验结果与复现边界.md`：结果解读及未复现部分。
- `experiment_results/`：逐种子原始结果和汇总统计。
- `results/`：seed=42机制演示结果。

所有实验均为合成任务的机制验证，不能当作原论文报告结果。记忆实验中的精确键检索结果不能外推到真实聊天。