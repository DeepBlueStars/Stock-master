import io

SRC = 'exit-ledger.html'
OUT = 'trade-plan.html'

src = io.open(SRC, encoding='utf-8').read()
lines = src.split('\n')
prefix = '\n'.join(lines[:581])
assert prefix.rstrip().endswith('<div class="page">')

extra_style = """
/* ===== 交易计划页专用 ===== */
.sum {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  margin-bottom: var(--s-5);
  border: 1px solid var(--rule);
  border-radius: var(--radius-sheet);
  background: var(--surface);
  box-shadow: var(--shadow-sheet);
  overflow: hidden;
}
.sum > div { padding: 16px 20px; border-right: 1px solid var(--rule); }
.sum > div:last-child { border-right: 0; }
.sum .k { display: block; font-size: var(--t-11); letter-spacing: 0.06em; color: var(--ink-3); }
.sum .v { display: block; margin-top: 4px; font-size: var(--t-17); font-weight: 600; letter-spacing: -0.01em; }
.sum .s { display: block; margin-top: 2px; font-size: var(--t-11); color: var(--ink-3); }

.week {
  display: flex; align-items: baseline; gap: 24px;
  padding: 14px 20px; margin-bottom: var(--s-5);
  border: 1px solid var(--rule); border-radius: var(--radius-sheet);
  background: var(--surface); font-size: var(--t-13); color: var(--ink-2);
}
.week strong { color: var(--ink); font-weight: 600; }
.week .spacer { flex: 1; }

.plan { padding: 18px 20px 16px; border-top: 1px solid var(--rule); }
.plan:first-of-type { border-top: 0; }

.plan-head { display: flex; align-items: baseline; gap: 12px; }
.plan-name { font-size: var(--t-15); font-weight: 600; }
.plan-code { font-size: var(--t-11); color: var(--ink-3); }
.plan-mode {
  font-size: var(--t-11); color: var(--ink-3);
  border: 1px solid var(--rule-strong); border-radius: 4px; padding: 1px 6px;
}
.plan-right { margin-left: auto; text-align: right; }
.plan-price { font-size: var(--t-17); font-weight: 600; letter-spacing: -0.01em; }
.plan-pct { font-size: var(--t-12); font-weight: 600; margin-left: 8px; }
.plan-warn {
  font-size: var(--t-11); color: var(--danger);
  border: 1px solid currentColor; border-radius: 4px; padding: 0 6px;
}
.plan-note {
  margin: 8px 0 0; padding: 0 20px 16px;
  font-size: var(--t-11); line-height: 1.7; color: var(--ink-3);
}

/* 批次打钩：16px 方框，点一下变「已买入」，不猜、只记录 */
/* 勾选列与其它数值列同向右对齐，表头才对得上 */
.ladder td.c-tick { text-align: right; }
.ladder th:last-child { text-align: right; }
.chk {
  appearance: none; -webkit-appearance: none;
  width: 18px; height: 18px; padding: 0; cursor: pointer;
  display: grid; place-items: center;
  border: 1px solid var(--rule-strong, #cfcdc5); border-radius: 3px;
  background: transparent;
  transition: background 140ms ease-out, border-color 140ms ease-out;
}
.chk:hover { border-color: var(--ink-3); }
.chk span {
  width: 9px; height: 9px; border-radius: 1px;
  background: var(--accent);
  transform: scale(0); opacity: 0;
  transition: transform 200ms cubic-bezier(.16,1,.3,1), opacity 140ms ease-out;
}
.chk[aria-pressed="true"] { border-color: var(--accent); }
.chk[aria-pressed="true"] span { transform: scale(1); opacity: 1; }
.chk:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }

.ladder tr.is-done td { color: var(--ink-3); }
.ladder tr.is-done .px { color: var(--ink-3); font-weight: 400; }

.plan-prog {
  font-size: var(--t-11); color: var(--ink-3);
  padding: 1px 6px; border: 1px solid var(--rule); border-radius: 999px;
  white-space: nowrap;
}
.plan-prog.is-full { color: var(--accent-ink, #24483d); border-color: var(--accent-line, #c3d6cd); background: var(--accent-wash, #e8f1ed); }

.ladder { width: 100%; border-collapse: collapse; table-layout: fixed; margin-top: 12px; font-size: var(--t-13); }
.ladder th {
  padding: 0 10px 6px; border-bottom: 1px solid var(--rule-strong);
  font-size: var(--t-11); font-weight: 400; letter-spacing: 0.06em; color: var(--ink-3); text-align: right;
}
.ladder th:first-child { text-align: left; }
.ladder td {
  padding: 9px 10px; border-bottom: 1px solid var(--rule);
  text-align: right; color: var(--ink-2);
}
.ladder td:first-child { text-align: left; color: var(--ink); }
.ladder tr:last-child td { border-bottom: 0; }
.ladder tr.is-done td { color: var(--ink-3); }
.ladder .px { font-size: var(--t-15); font-weight: 500; color: var(--ink); }
.ladder tr.is-done .px { color: var(--ink-3); font-weight: 400; }

.rule { display: flex; gap: 10px; margin-top: 12px; font-size: 12px; line-height: 1.7; color: var(--ink-2); }
.rule .k {
  flex: none; width: 64px; font-size: var(--t-11); letter-spacing: 0.06em;
  color: var(--ink-3); padding-top: 2px;
}
.rule .v { flex: 1; }
.rule .v .hand { color: var(--accent-ink); border-bottom: 1px solid var(--accent-line); }

.ref-btn .ref-note { margin-left: 12px; margin-right: auto; }

@media (max-width: 1080px) {
  .sum { grid-template-columns: repeat(2, 1fr); }
  .sum > div:nth-child(2) { border-right: 0; }
  .sum > div:nth-child(1), .sum > div:nth-child(2) { border-bottom: 1px solid var(--rule); }
}

@media (max-width: 900px) {
  .week { flex-wrap: wrap; gap: 8px 16px; }
}

@media (max-width: 820px) {
  .ladder, .ladder tbody, .ladder tr, .ladder td { display: block; width: auto; }
  .ladder thead { display: none; }
  .ladder tr { padding: 10px 0; border-bottom: 1px solid var(--rule); }
  .ladder td { border: 0; padding: 2px 0; display: flex; justify-content: space-between; }
  .ladder td::before {
    content: attr(data-label);
    font-size: var(--t-11); letter-spacing: 0.06em; color: var(--ink-3);
  }
  .ladder td:first-child { font-weight: 600; padding-bottom: 6px; }
  .ladder td:first-child::before { content: none; }
}

@media (max-width: 560px) {
  .plan { padding: 16px 16px 14px; }
  .rule { display: block; margin-top: 10px; }
  .rule .k { display: block; width: auto; margin-bottom: 2px; }
  .week { gap: 6px 14px; }
  .plan-head { flex-wrap: wrap; }
  .plan-right { margin-left: 0; text-align: left; }
  .sum > div { padding: 12px 16px; }
}
"""

prefix = prefix.replace('</style>', extra_style + '</style>', 1)
prefix = prefix.replace('StockAI · 今日决策 — 视觉稿', 'StockAI · 交易计划 — 视觉稿', 1)
prefix = prefix.replace('<a href="#" class="on">', '<a href="#">', 1)

_ICON_PLAN = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"><rect x="4" y="3" width="16" height="18" rx="2"/><path d="M8 8h8M8 12h8M8 16h5"/></svg>'
NAV_PLAN_OLD = '<a href="#">\n      ' + _ICON_PLAN + '\n      交易计划'
NAV_PLAN_NEW = '<a href="#" class="on">\n      ' + _ICON_PLAN + '\n      交易计划'
assert NAV_PLAN_OLD in prefix
prefix = prefix.replace(NAV_PLAN_OLD, NAV_PLAN_NEW, 1)


body = """
  <main class="stage">
    <div class="page">

      <div class="page-head">
        <div>
          <h1 class="page-title">交易计划</h1>
          <p class="page-sub">买入侧：先想清楚买多少、什么价买、跌了怎么办，再动手。</p>
        </div>
        <div class="page-meta">
          <div>
            <div class="meta-k">计划</div>
            <div class="meta-v num">3 只</div>
          </div>
          <div>
            <div class="meta-k">可用现金</div>
            <div class="meta-v num">¥12,400</div>
          </div>
        </div>
      </div>

      <div class="sum">
        <div>
          <span class="k">已投入</span>
          <span class="v num">¥5,436</span>
          <span class="s">3 只 · 7 个批次</span>
        </div>
        <div>
          <span class="k">浮动</span>
          <span class="v num delta-up">+¥397</span>
          <span class="s">+7.3%</span>
        </div>
        <div>
          <span class="k">已买入</span>
          <span class="v num" id="sumTick">0 / 7</span>
          <span class="s">批次 · 手动确认</span>
        </div>
        <div>
          <span class="k">现金占比</span>
          <span class="v num">70%</span>
          <span class="s">中性区间 60–70%</span>
        </div>
      </div>

      <div class="week">
        <span>本周组合</span>
        <span>成本 <strong class="num">¥5,436</strong></span>
        <span>市值 <strong class="num">¥5,833</strong></span>
        <span class="delta-up num"><strong>+7.3%</strong></span>
        <span class="spacer"></span>
        <span>建议：<strong>持有，本周不加仓</strong></span>
      </div>

      <section class="sheet">
        <div class="pane-head">
          <h2 class="pane-title">持仓交易计划</h2>
          <span class="pane-note">买入后自动生成；改成本或加减仓会重算</span>
        </div>

        <div class="plan">
          <div class="plan-head">
            <span class="plan-name">科创芯片ETF</span>
            <span class="plan-code num">588200</span>
            <span class="plan-mode">分批买入</span>
            <span class="plan-prog">0/3 已买入</span>
            <div class="plan-right">
              <span class="plan-price num">0.882</span>
              <span class="plan-pct delta-up num">+48.5%</span>
            </div>
          </div>

          <table class="ladder">
            <thead>
              <tr><th>批次</th><th>比例</th><th>触发价</th><th>距现价</th><th>已买入</th></tr>
            </thead>
            <tbody>
              <tr>
                <td>第 1 批</td><td data-label="比例">50%</td>
                <td data-label="触发价"><span class="px num">0.882</span></td>
                <td data-label="距现价" class="num">0.0%</td>
                <td data-label="已买入" class="c-tick"><button class="chk" type="button" aria-pressed="false" aria-label="标记这批已买入"><span></span></button></td>
              </tr>
              <tr>
                <td>第 2 批</td><td data-label="比例">30%</td>
                <td data-label="触发价"><span class="px num">0.860</span></td>
                <td data-label="距现价" class="num">−2.5%</td>
                <td data-label="已买入" class="c-tick"><button class="chk" type="button" aria-pressed="false" aria-label="标记这批已买入"><span></span></button></td>
              </tr>
              <tr>
                <td>第 3 批</td><td data-label="比例">20%</td>
                <td data-label="触发价"><span class="px num">0.840</span></td>
                <td data-label="距现价" class="num">−4.8%</td>
                <td data-label="已买入" class="c-tick"><button class="chk" type="button" aria-pressed="false" aria-label="标记这批已买入"><span></span></button></td>
              </tr>
            </tbody>
          </table>

          <div class="rule">
            <span class="k">买入依据</span>
            <span class="v">ATR 占现价 5.26%（高波动），按 5:3:2 分三批；第 2、3 批各比前一批低 0.5 个 ATR。</span>
          </div>
          <div class="rule">
            <span class="k">补仓规则</span>
            <span class="v">跌破 0.86 补 30%，再破 0.84 补 20%；若跌破 20 日支撑 0.79 就不再补。</span>
          </div>
        </div>

        <div class="plan">
          <div class="plan-head">
            <span class="plan-name">中韩半导体ETF</span>
            <span class="plan-code num">513310</span>
            <span class="plan-mode">分批买入</span>
            <span class="plan-prog">0/3 已买入</span>
            <span class="plan-warn">接近止损</span>
            <div class="plan-right">
              <span class="plan-price num">3.640</span>
              <span class="plan-pct delta-down num">−15.5%</span>
            </div>
          </div>

          <table class="ladder">
            <thead>
              <tr><th>批次</th><th>比例</th><th>触发价</th><th>距现价</th><th>已买入</th></tr>
            </thead>
            <tbody>
              <tr>
                <td>第 1 批</td><td data-label="比例">50%</td>
                <td data-label="触发价"><span class="px num">3.640</span></td>
                <td data-label="距现价" class="num">0.0%</td>
                <td data-label="已买入" class="c-tick"><button class="chk" type="button" aria-pressed="false" aria-label="标记这批已买入"><span></span></button></td>
              </tr>
              <tr>
                <td>第 2 批</td><td data-label="比例">30%</td>
                <td data-label="触发价"><span class="px num">3.590</span></td>
                <td data-label="距现价" class="num">−1.4%</td>
                <td data-label="已买入" class="c-tick"><button class="chk" type="button" aria-pressed="false" aria-label="标记这批已买入"><span></span></button></td>
              </tr>
              <tr>
                <td>第 3 批</td><td data-label="比例">20%</td>
                <td data-label="触发价"><span class="px num">3.590</span></td>
                <td data-label="距现价" class="num">−1.4%</td>
                <td data-label="已买入" class="c-tick"><button class="chk" type="button" aria-pressed="false" aria-label="标记这批已买入"><span></span></button></td>
              </tr>
            </tbody>
          </table>

          <div class="rule">
            <span class="k">买入依据</span>
            <span class="v">ATR 占现价 4.82%（高波动），按 5:3:2；第 2、3 批都被 20 日支撑 3.59 截断，实际只有一个补仓价位。</span>
          </div>
          <div class="rule">
            <span class="k">补仓规则</span>
            <span class="v">跌破 3.59 视为判断错，<span class="hand">不再补</span>；支撑失守还加仓，等于摊平亏损。</span>
          </div>
        </div>

        <div class="plan">
          <div class="plan-head">
            <span class="plan-name">红利ETF易方达</span>
            <span class="plan-code num">515180</span>
            <span class="plan-mode">一次性买入</span>
            <span class="plan-prog">0/1 已买入</span>
            <div class="plan-right">
              <span class="plan-price num">1.353</span>
              <span class="plan-pct delta-up num">+1.3%</span>
            </div>
          </div>

          <table class="ladder">
            <thead>
              <tr><th>批次</th><th>比例</th><th>触发价</th><th>距现价</th><th>已买入</th></tr>
            </thead>
            <tbody>
              <tr>
                <td>一次性</td><td data-label="比例">100%</td>
                <td data-label="触发价"><span class="px num">1.353</span></td>
                <td data-label="距现价" class="num">0.0%</td>
                <td data-label="已买入" class="c-tick"><button class="chk" type="button" aria-pressed="false" aria-label="标记这批已买入"><span></span></button></td>
              </tr>
            </tbody>
          </table>

          <div class="rule">
            <span class="k">买入依据</span>
            <span class="v">ATR 占现价 1.64%（低波动），一次性买入；等回踩的期望收益抵不过等不到的机会成本。</span>
          </div>
          <div class="rule">
            <span class="k">补仓规则</span>
            <span class="v"><span class="hand">没有补仓规则</span>。低波动品种一次建完，跌了是行情变了，不是给你折扣。</span>
          </div>
        </div>

        <p class="plan-note">金额暂不显示：现在的算法是「每只计划各自拿全部可用现金当分母」，三只加起来会超过账户现金，等分配逻辑修好再放回来。批次是否已买入由你手动打钩确认，系统不猜。</p>
      </section>

      <section class="sheet">
        <button class="ref-btn" type="button" aria-expanded="false" id="refBtn">
          <span class="ref-title">参考信息</span>
          <span class="ref-note">市场状态、可用现金口径、生成规则</span>
          <svg class="chev" viewBox="0 0 12 12" aria-hidden="true">
            <path d="M3 4.5 6 7.5 9 4.5" fill="none" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </button>
        <div class="ref-panel" id="refPanel">
          <div><span class="k">市场状态</span><span class="v">震荡 · 中性仓位（建议现金 60–70%）</span></div>
          <div><span class="k">可用现金</span><span class="v">按账户现金 − 已挂单计算</span></div>
          <div><span class="k">生成时机</span><span class="v">买入后自动生成；加减仓后重算</span></div>
          <div><span class="k">降级说明</span><span class="v">K 线不足 15 根时按固定百分比给价</span></div>
        </div>
      </section>

      <p class="preview-note">视觉稿 · 数据为示例</p>
    </div>
  </main>
</div>

<script>
(function () {
  requestAnimationFrame(function () {
    requestAnimationFrame(function () { document.body.classList.add('is-ready'); });
  });
  var refBtn = document.getElementById('refBtn');
  var refPanel = document.getElementById('refPanel');
  refBtn.addEventListener('click', function () {
    var open = refBtn.getAttribute('aria-expanded') === 'true';
    refBtn.setAttribute('aria-expanded', String(!open));
    refPanel.classList.toggle('open', !open);
  });
})();
</script>

<script>
// 批次打钩：纯前端演示，真实版会 PATCH 到 trade_plan_batches
if (location.hash === '#done') {
  const first = document.querySelectorAll('.plan')[0].querySelectorAll('.chk');
  first[0].setAttribute('aria-pressed','true');
  first[0].closest('tr').classList.add('is-done');
}

document.querySelectorAll('.plan').forEach((plan) => {
  const chip = plan.querySelector('.plan-prog');
  const boxes = [...plan.querySelectorAll('.chk')];
  if (!chip || !boxes.length) return;
  const total = boxes.length;
  const sync = () => {
    const done = boxes.filter((b) => b.getAttribute('aria-pressed') === 'true').length;
    chip.textContent = `${done}/${total} 已买入`;
    chip.classList.toggle('is-full', done === total);
  };
  boxes.forEach((box) => {
    box.addEventListener('click', () => {
      const on = box.getAttribute('aria-pressed') === 'true';
      box.setAttribute('aria-pressed', on ? 'false' : 'true');
      box.closest('tr').classList.toggle('is-done', !on);
      sync();
    });
  });
  sync();
});

// 顶部汇总跟着更新
const sumTick = document.getElementById('sumTick');
if (sumTick) {
  const all = () => [...document.querySelectorAll('.chk')];
  if (location.hash === '#done') sumTick.textContent = '1 / 7';
  const refreshSum = () => {
    const done = all().filter((b) => b.getAttribute('aria-pressed') === 'true').length;
    sumTick.textContent = `${done} / ${all().length}`;
  };
  document.addEventListener('click', (e) => {
    if (e.target.closest('.chk')) refreshSum();
  });
}
</script>
</body>
</html>
"""

io.open(OUT, 'w', encoding='utf-8').write(prefix + body)
print('written', len(prefix + body))
