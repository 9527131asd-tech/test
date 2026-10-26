"""验证实现的数学与持久性；测试通过不代表论文复现成功。"""
import json
import tempfile
import unittest
from pathlib import Path
from lab import Memory, clipped_objective_grad, softmax, token_units
from lab import search, supervised, reward_learning, planning


class MechanismTests(unittest.TestCase):
    def test_gradient_finite_difference(self):
        # 覆盖正/负优势以及比率裁剪生效的区域，避开不可导边界。
        for weights in ([.05,-.05,.02,-.02], [1.5,-1.0,.2,-.7]):
            old = [.25]*4
            actions, advantages = [0,1,2,3], [1.,-1.,.5,-.5]
            _, grad = clipped_objective_grad(weights,old,actions,advantages)
            for j in range(4):
                plus,minus = list(weights),list(weights)
                plus[j] += 1e-6
                minus[j] -= 1e-6
                f1,_ = clipped_objective_grad(plus,old,actions,advantages)
                f0,_ = clipped_objective_grad(minus,old,actions,advantages)
                self.assertAlmostEqual(grad[j], (f1-f0)/2e-6, places=6)

    def test_zero_advantages_are_finite(self):
        value,grad = clipped_objective_grad([0.]*4,[.25]*4,[0,1],[0.,0.])
        self.assertEqual(value,0.)
        self.assertEqual(grad,[0.]*4)

    def test_memory_survives_restart_and_keeps_versions(self):
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp)/'memory.sqlite'
            m=Memory(path)
            m.put('茶','绿茶');m.put('茶','红茶');m.close()
            m=Memory(path)
            result=m.retrieve('茶',10)
            self.assertIn('红茶',result['context'])
            self.assertNotIn('绿茶',result['context'])
            self.assertEqual(m.db.execute('SELECT COUNT(*) FROM events').fetchone()[0],2)
            self.assertEqual(m.retrieve('茶',0)['context'],'')
            self.assertEqual(m.retrieve('unknown',50)['context'],'')
            self.assertLessEqual(result['units'],10)
            self.assertEqual(result['units'],len(token_units(result['context'])))
            with self.assertRaises(ValueError):m.retrieve('茶',-1)
            m.close()

    def test_parameter_training_and_checkpoint(self):
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp)
            result=supervised(path,42)
            self.assertGreater(result['after_accuracy'],result['before_accuracy'])
            w=json.loads((path/'supervised_weights.json').read_text())['weights']
            self.assertTrue(any(v!=0 for row in w for v in row))
            result=reward_learning(path,42)
            w=json.loads((path/'policy_weights.json').read_text())
            self.assertEqual(softmax(w),result['probabilities'])
            self.assertGreater(result['final_reward'],result['initial_reward'])

    def test_search_reproducibility_and_legal_plan(self):
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp)
            a=search(path,42);b=search(path,42)
            self.assertEqual(a,b)
            self.assertLessEqual(a['train_mse'],a['trace'][0]['train_mse'])
            route=planning(path,42)['path']
            self.assertEqual(route[0],(0,0));self.assertEqual(route[-1],(3,3))
            for a,b in zip(route,route[1:]):
                self.assertEqual(abs(a[0]-b[0])+abs(a[1]-b[1]),1)
                self.assertNotIn(b,{(1,0),(1,1),(2,2)})


if __name__=='__main__':unittest.main()
