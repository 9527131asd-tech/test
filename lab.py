"""15篇论文的机制教学实验。Python 3.10+；标准库；不调用LLM/API。

运行 python lab.py all --out results。所有代理均为显式规则代理。
没有复现原论文的模型、数据、临床/化学任务或报告指标。
"""
from __future__ import annotations
import argparse
import csv
import json
import math
import random
import re
import sqlite3
from pathlib import Path


def save(path, data):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")


def softmax(weights):
    values = [math.exp(x - max(weights)) for x in weights]
    return [v / sum(values) for v in values]


def token_units(text):
    """可复核的代理单位，绝不是任何LLM tokenizer的计费token。"""
    return re.findall(r"[\u4e00-\u9fff]|[A-Za-z0-9_]+|[^\w\s]", text.lower())


def coordination(out, seed):
    """同一工具，两种固定路由。结构是手写的，不是训练学出来的。"""
    tasks = [(a, b, op) for a in range(1, 8) for b in range(1, 5)
             for op in ('add', 'mul')]
    traces = []
    for mode in ('single_tool_router', 'specialist_router'):
        correct = 0
        for a, b, op in tasks:
            # 单代理也拥有相同工具，避免把工具权限差异当作协作收益。
            tool = op if mode == 'single_tool_router' else {'add': 'add', 'mul': 'mul'}[op]
            answer = a + b if tool == 'add' else a * b
            correct += answer == (a + b if op == 'add' else a * b)
        traces.append(dict(mode=mode, accuracy=correct / len(tasks),
                           tool_calls=len(tasks), weight_updates=0))
    result = dict(mechanism='fixed orchestration', results=traces,
                  note='两个规则路由器同样正确；不预设多代理收益。未模拟LLM协作。')
    save(out / 'coordination.json', result)
    return result


def revision(out, seed):
    """当前答案修订；验证器提供真实约束方向，不保存经验。"""
    trace, value = [], 0
    target = 7
    for step in range(12):
        error = value - target
        feedback = 'accepted' if error == 0 else ('too_low' if error < 0 else 'too_high')
        trace.append(dict(step=step, value=value, feedback=feedback))
        if feedback == 'accepted':
            break
        value += 1 if feedback == 'too_low' else -1
    result = dict(mechanism='task-local revision', trace=trace, answer=value,
                  baseline_without_feedback=0, new_session_initial_value=0,
                  note='验证器用已知目标给反馈；这是人工构造任务，不是LLM批评。')
    save(out / 'revision.json', result)
    return result


def planning(out, seed):
    """MAP的模块分工类比：候选、状态转移、合法性检查、搜索；不是MAP复现。"""
    start, goal = (0, 0), (3, 3)
    walls = {(1, 0), (1, 1), (2, 2)}
    frontier, visited = [[start]], {start}
    expanded = 0
    while frontier:
        path = frontier.pop(0)
        x, y = path[-1]
        if (x, y) == goal:
            result = dict(path=path, expanded=expanded, algorithm='breadth-first search',
                          weight_updates=0, note='BFS教学替身；不声称实现原文LLM规划器。')
            save(out / 'planning.json', result)
            return result
        expanded += 1
        for dx, dy in ((1, 0), (0, 1), (-1, 0), (0, -1)):
            nxt = x + dx, y + dy
            if (0 <= nxt[0] < 4 and 0 <= nxt[1] < 4 and
                    nxt not in walls and nxt not in visited):
                visited.add(nxt)
                frontier.append(path + [nxt])
    raise RuntimeError('No valid plan')


def search(out, seed):
    """外部种群+执行评价。候选只是线性程序a*x+b，不执行任意生成代码。"""
    rng = random.Random(seed)
    train = [(x, 3 * x + 2) for x in range(-4, 5)]
    test = [(x, 3 * x + 2) for x in range(5, 12)]
    def score(candidate, data):
        a, b = candidate
        return -sum((a * x + b - y) ** 2 for x, y in data) / len(data)
    population = {(0, 0)}
    trace = []
    for iteration in range(35):
        elite = sorted(population, key=lambda c: (-score(c, train), c))[:4]
        parent = rng.choice(elite)
        children = [(parent[0]+d, parent[1]) for d in (-1, 1)]
        children += [(parent[0], parent[1]+d) for d in (-1, 1)]
        population.update(c for c in children if all(abs(v) <= 12 for v in c))
        best = max(sorted(population), key=lambda c: score(c, train))
        trace.append(dict(iteration=iteration, best=best, train_mse=-score(best, train)))
    best = max(sorted(population), key=lambda c: score(c, train))
    # 同一评价次数的随机候选基线，不提前看test选择候选。
    random_candidates = [(rng.randint(-12, 12), rng.randint(-12, 12))
                         for _ in range(len(population))]
    random_best = max(random_candidates, key=lambda c: score(c, train))
    save(out / 'program_population.json', sorted(population))
    result = dict(best=best, train_mse=-score(best, train), test_mse=-score(best, test),
                  unique_evaluations=len(population), random_test_mse=-score(random_best, test),
                  trace=trace, retained='program_population.json', weight_updates=0,
                  note='数值变异代替LLM；没有FunSearch岛屿机制、LegoNE证明器或科研锦标赛。')
    save(out / 'search.json', result)
    return result


def supervised(out, seed):
    """生成者/解答者/验证者产出数据，训练微型softmax路由器。"""
    rng = random.Random(seed)
    words = ['sum', 'total', 'product', 'times']
    weights = [[0.0, 0.0] for _ in words]
    rows = []
    for word_id, word in enumerate(words):
        for _ in range(35):
            a, b = rng.randrange(1, 10), rng.randrange(1, 10)
            # 解答者可提错工具，验证者用独立的算术标签拒收错误样本。
            label = int(word_id >= 2)
            proposed = label if rng.random() > .2 else 1-label
            answer = a + b if proposed == 0 else a * b
            expected = a + b if label == 0 else a * b
            if answer == expected and proposed == label:
                rows.append((word_id, label, a, b))
    def accuracy():
        # 只检验同词汇下的新数字，绝不声称未见词汇泛化。
        correct = 0
        for i in range(4):
            predicted = max(range(2), key=lambda j: weights[i][j])
            for a in range(11, 20):
                correct += predicted == int(i >= 2)
        return correct / 36
    before = accuracy()
    for epoch in range(40):
        rng.shuffle(rows)
        for i, label, a, b in rows:
            probs = softmax(weights[i])
            for j in range(2):
                weights[i][j] += .1 * (int(j == label) - probs[j])
    save(out / 'supervised_weights.json', dict(words=words, weights=weights))
    save(out / 'synthetic_data.json', rows)
    result = dict(before_accuracy=before, after_accuracy=accuracy(), accepted_samples=len(rows),
                  retained='supervised_weights.json', parameters=8,
                  note='真正更新8个标量参数；不是LLM微调，也不是临床自博弈。')
    save(out / 'supervised.json', result)
    return result


def clipped_objective_grad(weights, old, actions, advantages, beta=.02, clip=.2):
    """单步分类策略的clipped surrogate + 精确KL。返回目标与解析梯度。

    教学改写：不是R1 token级GRPO；精确KL替代论文采样KL估计。
    """
    p = softmax(weights)
    ref = [1 / len(p)] * len(p)
    kl = sum(pi * math.log(pi / ri) for pi, ri in zip(p, ref))
    value, grad = -beta * kl, [0.0] * len(p)
    for action, advantage in zip(actions, advantages):
        ratio = p[action] / old[action]
        clipped = max(1-clip, min(1+clip, ratio))
        value += min(ratio * advantage, clipped * advantage) / len(actions)
        active = not ((advantage > 0 and ratio > 1+clip) or
                      (advantage < 0 and ratio < 1-clip))
        if active:
            for j in range(len(p)):
                grad[j] += advantage * ratio * (int(action == j)-p[j]) / len(actions)
    for j in range(len(p)):
        grad[j] -= beta * p[j] * (math.log(p[j] / ref[j])-kl)
    return value, grad


def reward_learning(out, seed):
    """组内相对奖励、旧策略比率、裁剪、KL；只学4动作bandit。"""
    rng = random.Random(seed)
    weights, rewards, trace = [0.0]*4, [0.0, .2, .5, 1.0], []
    initial = sum(p*r for p, r in zip(softmax(weights), rewards))
    for step in range(200):
        old = softmax(weights)
        actions = rng.choices(range(4), weights=old, k=16)
        scores = [rewards[a] for a in actions]
        mean = sum(scores) / len(scores)
        std = math.sqrt(sum((r-mean)**2 for r in scores) / len(scores))
        advantages = [(r-mean)/(std+1e-8) for r in scores]
        for _ in range(4):
            objective, grad = clipped_objective_grad(weights, old, actions, advantages)
            weights = [w + .15*g for w, g in zip(weights, grad)]
        trace.append(dict(step=step, probabilities=softmax(weights),
                          expected_reward=sum(p*r for p, r in zip(softmax(weights), rewards))))
    save(out / 'policy_weights.json', weights)
    result = dict(initial_reward=initial, final_reward=trace[-1]['expected_reward'],
                  probabilities=softmax(weights), trace=trace, retained='policy_weights.json',
                  note='GRPO思想的单步bandit教学版本，不是DeepSeek-R1复现；没有语言泛化测试。')
    save(out / 'reward.json', result)
    return result


class Memory:
    """追加式历史+最新事实索引。检索不删除原始记录，状态落盘可重启。

    只接受用户显式结构化事实，不宣称从自然语言自动抽取。
    每个key只返回最新版本；旧版本仍存于events。
    """
    def __init__(self, path):
        path = Path(path)
        path.parent.mkdir(parents=True, exist_ok=True)
        self.db = sqlite3.connect(path)
        self.db.executescript('''
          CREATE TABLE IF NOT EXISTS events (
            id INTEGER PRIMARY KEY, key TEXT NOT NULL, value TEXT NOT NULL);
          CREATE TABLE IF NOT EXISTS current (
            key TEXT PRIMARY KEY, event_id INTEGER NOT NULL);
          CREATE TABLE IF NOT EXISTS terms (
            term TEXT, key TEXT, PRIMARY KEY(term,key));
          CREATE INDEX IF NOT EXISTS term_lookup ON terms(term);
        ''')

    def put(self, key, value):
        if not key.strip() or not value.strip():
            raise ValueError('key/value must not be empty')
        with self.db:
            event_id = self.db.execute('INSERT INTO events(key,value) VALUES (?,?)',
                                       (key, value)).lastrowid
            self.db.execute('INSERT OR REPLACE INTO current VALUES (?,?)', (key,event_id))
            self.db.execute('DELETE FROM terms WHERE key=?', (key,))
            self.db.executemany('INSERT INTO terms VALUES (?,?)',
                                [(t,key) for t in set(token_units(key+' '+value))])

    def retrieve(self, query, budget=80):
        if budget < 0:
            raise ValueError('budget must be nonnegative')
        tokens = set(token_units(query))
        if not tokens:
            return dict(context='', units=0, matches=[], matched_candidates=0)
        marks = ','.join('?' for _ in tokens)
        rows = self.db.execute(f'''SELECT e.id,e.key,e.value,COUNT(*) AS relevance
          FROM terms t JOIN current c ON c.key=t.key JOIN events e ON e.id=c.event_id
          WHERE t.term IN ({marks}) GROUP BY e.id ORDER BY relevance DESC,e.id DESC''',
                               tuple(tokens)).fetchall()
        selected, used = [], 0
        for event_id,key,value,relevance in rows:
            line = f'{key}: {value}'
            units = len(token_units(line))
            if used + units <= budget:
                selected.append(dict(event_id=event_id, key=key,value=value,text=line))
                used += units
        return dict(context='\n'.join(x['text'] for x in selected), units=used,
                    matches=selected, matched_candidates=len(rows))

    def close(self):
        self.db.close()


def memory_demo(out, seed):
    # 演示DB专用名称，重复运行只重建演示DB，不接触用户memory.sqlite。
    path = out / 'demo_memory.sqlite'
    if path.exists():
        path.unlink()
    store = Memory(path)
    store.put('user_tea', 'green')
    for i in range(100):
        store.put(f'project_{i}', f'milestone_{i} recorded in the project diary')
    store.put('user_tea', 'black')
    store.close()
    store = Memory(path)  # 新连接读取持久状态
    retrieved = store.retrieve('user_tea', budget=12)
    history = store.db.execute('SELECT key,value FROM events ORDER BY id').fetchall()
    full_units = len(token_units('\n'.join(f'{k}: {v}' for k,v in history)))
    result = dict(history_rows=len(history), full_history_units=full_units,
                  retrieval=retrieved, original_versions_preserved=True,
                  note='构造的精确键查询；units不是计费token。没有真实对话记忆准确率证据。')
    store.close()
    save(out / 'memory.json', result)
    return result


DEMOS = dict(coordination=coordination, planning=planning, revision=revision,
             search=search, supervised=supervised, reward=reward_learning, memory=memory_demo)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('demo', choices=['all', *DEMOS, 'remember', 'recall'])
    parser.add_argument('--out', type=Path, default=Path('results'))
    parser.add_argument('--seed', type=int, default=42)
    parser.add_argument('--db', type=Path, default=Path('memory.sqlite'))
    parser.add_argument('--key', default='')
    parser.add_argument('--value', default='')
    parser.add_argument('--query', default='')
    parser.add_argument('--budget', type=int, default=80)
    args = parser.parse_args()
    if args.demo in ('remember', 'recall'):
        memory = Memory(args.db)
        try:
            if args.demo == 'remember':
                memory.put(args.key, args.value)
                result = dict(saved=True, key=args.key)
            else:
                result = memory.retrieve(args.query, args.budget)
        finally:
            memory.close()
    else:
        args.out.mkdir(parents=True, exist_ok=True)
        selected = DEMOS if args.demo == 'all' else {args.demo: DEMOS[args.demo]}
        result = {name: fn(args.out,args.seed) for name,fn in selected.items()}
        save(args.out / 'summary.json', result)
        result = {k:{a:b for a,b in v.items() if a != 'trace'} for k,v in result.items()}
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
