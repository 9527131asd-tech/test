"""可重复的小型机制实验。每行一seed；不能外推为原论文结果。"""
import csv, json, random, tempfile, statistics
from pathlib import Path
from lab import search, supervised, reward_learning, Memory, token_units

OUT=Path('experiment_results'); OUT.mkdir(exist_ok=True)
rows=[]
for seed in range(30):
    with tempfile.TemporaryDirectory() as td:
        p=Path(td)
        s=search(p,seed)
        rows.append(dict(seed=seed, experiment='external_search',
          metric='held_out_mse', method='evolutionary_search', value=s['test_mse']))
        rows.append(dict(seed=seed, experiment='external_search',
          metric='held_out_mse', method='random_candidates', value=s['random_test_mse']))
        sup=supervised(p,seed)
        rows.append(dict(seed=seed,experiment='synthetic_training',metric='accuracy',
          method='trained_parameters',value=sup['after_accuracy']))
        rows.append(dict(seed=seed,experiment='synthetic_training',metric='accuracy',
          method='untrained_parameters',value=sup['before_accuracy']))
        rew=reward_learning(p,seed)
        rows.append(dict(seed=seed,experiment='reward_bandit',metric='expected_reward',
          method='group_relative_update',value=rew['final_reward']))
        rows.append(dict(seed=seed,experiment='reward_bandit',metric='expected_reward',
          method='uniform_policy',value=rew['initial_reward']))
    # 100 facts, 50 queries, one update to a chosen fact; query budget only context.
    db=OUT/f'seed_{seed}.sqlite'
    if db.exists(): db.unlink()
    m=Memory(db)
    for j in range(100): m.put(f'fact_{j}',f'value_{j}')
    for j in range(0,100,2): m.put(f'fact_{j}',f'updated_{j}')
    history=m.db.execute('SELECT key,value FROM events ORDER BY id').fetchall()
    full_history_units=len(token_units('\n'.join(f'{k}: {v}' for k,v in history)))
    retrieved=correct=0; used=0
    for j in range(100):
        result=m.retrieve(f'fact_{j}',budget=8)
        retrieved+=bool(result['matches']); used+=result['units']
        if result['matches'] and result['matches'][0]['value']==(f'updated_{j}' if j%2==0 else f'value_{j}'):
            correct+=1
    rows.append(dict(seed=seed,experiment='persistent_memory',metric='query_recall',
      method='indexed_latest_fact',value=retrieved/100))
    rows.append(dict(seed=seed,experiment='persistent_memory',metric='correct_latest_value',
      method='indexed_latest_fact',value=correct/100))
    rows.append(dict(seed=seed,experiment='persistent_memory',metric='mean_context_units',
      method='indexed_latest_fact',value=used/100))
    rows.append(dict(seed=seed,experiment='persistent_memory',metric='full_history_units_per_query',
      method='full_history_replay',value=full_history_units))
    rows.append(dict(seed=seed,experiment='persistent_memory',metric='retrieved_context_units_per_query',
      method='indexed_latest_fact',value=used/100))
    m.close();db.unlink()
with (OUT/'runs.csv').open('w',newline='',encoding='utf-8-sig') as f:
    w=csv.DictWriter(f,fieldnames=rows[0].keys());w.writeheader();w.writerows(rows)
summary=[]
for exp in sorted({x['experiment'] for x in rows}):
 for metric in sorted({x['metric'] for x in rows if x['experiment']==exp}):
  methods=sorted({x['method'] for x in rows if x['experiment']==exp and x['metric']==metric})
  for method in methods:
   vals=[x['value'] for x in rows if x['experiment']==exp and x['metric']==metric and x['method']==method]
   summary.append(dict(experiment=exp,metric=metric,method=method,n=len(vals),mean=statistics.mean(vals),
                       sd=statistics.stdev(vals),minimum=min(vals),maximum=max(vals)))
with (OUT/'summary.csv').open('w',newline='',encoding='utf-8-sig') as f:
 w=csv.DictWriter(f,fieldnames=summary[0].keys());w.writeheader();w.writerows(summary)
(OUT/'summary.json').write_text(json.dumps(summary,indent=2,ensure_ascii=False),encoding='utf-8')
print(json.dumps(summary,indent=2))
