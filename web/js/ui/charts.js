// Inline SVG bar charts (the desktop app draws these with Java2D). Marks follow the dataviz rules:
// thin bars (<= 24px) with a 4px rounded data-end and a square baseline, hairline gridlines,
// value labels in text tokens, a hover/keyboard tooltip on every mark, and a table view for screen readers.
// Charts redraw at their container's real width (ResizeObserver) so text stays at its CSS size.
import { h, svgEl } from './dom.js';

const NS = 'http://www.w3.org/2000/svg';

function roundedTopRect(x, y, w, hgt, r) {
  const rr = Math.min(r, w / 2, hgt);
  return `M${x},${y + hgt} V${y + rr} Q${x},${y} ${x + rr},${y} H${x + w - rr} Q${x + w},${y} ${x + w},${y + rr} V${y + hgt} Z`;
}

function roundedRightRect(x, y, w, hgt, r) {
  const rr = Math.min(r, hgt / 2, w);
  return `M${x},${y} H${x + w - rr} Q${x + w},${y} ${x + w},${y + rr} V${y + hgt - rr} Q${x + w},${y + hgt} ${x + w - rr},${y + hgt} H${x} Z`;
}

/** Clean tick step so the axis reads 0 / 10 / 20 / 30 rather than thirds of an arbitrary max. */
function niceScale(max) {
  if (max <= 0) return { max: 1, ticks: [0, 1] };
  const steps = [1, 2, 5, 10, 20, 25, 50, 100, 200, 250, 500, 1000];
  const step = steps.find((s) => max / s <= 4) || Math.pow(10, Math.ceil(Math.log10(max)));
  const top = Math.ceil(max / step) * step;
  const ticks = [];
  for (let t = 0; t <= top; t += step) ticks.push(t);
  return { max: top, ticks };
}

function tableView(title, data, valueLabel) {
  return h('table.sr-only', h('caption', title), h('thead', h('tr', h('th', 'Category'), h('th', valueLabel))),
    h('tbody', data.map(([k, v]) => h('tr', h('td', k), h('td', v)))));
}

/** Draws now and again whenever the wrapper's width changes. */
function responsive(wrap, draw) {
  let last = 0;
  const render = () => {
    const w = Math.max(240, Math.floor(wrap.clientWidth || 0));
    if (w === last) return;
    last = w;
    wrap.querySelector('svg')?.remove();
    wrap.prepend(draw(w));
  };
  if (typeof ResizeObserver !== 'undefined') new ResizeObserver(render).observe(wrap);
  queueMicrotask(render);
  requestAnimationFrame(render);
  return wrap;
}

function tooltip(g, text) {
  const t = document.createElementNS(NS, 'title');
  t.textContent = text;
  g.append(t);
}

/**
 * Vertical columns. data: [[label, value], ...]. color(label) -> css color.
 * Every column carries its value on the cap (the desktop app does the same and there are only 10).
 */
export function columnChart(data, { color, title, valueLabel = 'Count', emptyText = 'No data', height = 200 } = {}) {
  const wrap = h('div.chart-body.chart');
  if (!data.length || data.every(([, v]) => v === 0)) {
    wrap.append(h('div.chart-empty', emptyText));
    return wrap;
  }
  wrap.append(tableView(title, data, valueLabel));
  const scale = niceScale(Math.max(...data.map(([, v]) => v)));
  return responsive(wrap, (width) => {
    const iw0 = width - 38;
    const tight = iw0 / data.length < 32; // rotate category labels when the slots are too narrow for "1.00"
    const pad = { top: 22, right: 8, bottom: tight ? 34 : 26, left: 30 };
    const iw = width - pad.left - pad.right;
    const ih = height - pad.top - pad.bottom;
    const svg = svgEl('svg', { viewBox: `0 0 ${width} ${height}`, width, height, role: 'img', 'aria-label': title });
    const grid = svgEl('g', { class: 'grid' });
    for (const t of scale.ticks) {
      const y = pad.top + ih - (t / scale.max) * ih;
      grid.append(svgEl('line', { x1: pad.left, x2: width - pad.right, y1: y, y2: y }));
      const label = svgEl('text', { x: pad.left - 8, y: y + 4, 'text-anchor': 'end' });
      label.textContent = t;
      grid.append(label);
    }
    svg.append(grid);
    const slot = iw / data.length;
    const bw = Math.min(24, Math.max(8, slot * 0.55));
    data.forEach(([label, value], i) => {
      const x = pad.left + slot * i + (slot - bw) / 2;
      const bh = Math.max(value > 0 ? 3 : 0, (value / scale.max) * ih);
      const y = pad.top + ih - bh;
      const g = svgEl('g', { class: 'bar-group', tabindex: '0', role: 'listitem' });
      tooltip(g, `${label}: ${value} ${valueLabel.toLowerCase()}`);
      g.append(svgEl('rect', { class: 'hit', x: pad.left + slot * i, y: pad.top - 16, width: slot, height: ih + 16 }));
      g.append(svgEl('path', { class: 'bar', d: roundedTopRect(x, y, bw, bh, 4), fill: color(label) }));
      const v = svgEl('text', { class: 'value-label', x: x + bw / 2, y: y - 6, 'text-anchor': 'middle' });
      v.textContent = value;
      g.append(v);
      const cx = x + bw / 2;
      const cat = tight
        ? svgEl('text', { x: cx, y: pad.top + ih + 8, 'text-anchor': 'end', 'font-size': '11', transform: `rotate(-45 ${cx} ${pad.top + ih + 8})` })
        : svgEl('text', { x: cx, y: height - 6, 'text-anchor': 'middle', 'font-size': slot < 36 ? '11' : '12' });
      cat.textContent = label;
      g.append(cat);
      svg.append(g);
    });
    svg.append(svgEl('line', { x1: pad.left, x2: width - pad.right, y1: pad.top + ih, y2: pad.top + ih, stroke: 'var(--border-strong)' }));
    return svg;
  });
}

/** Horizontal bars with the category on the left and the value at the tip. */
export function barChart(data, { color, title, valueLabel = 'Students', emptyText = 'No data', rowHeight = 40, legend = null } = {}) {
  const wrap = h('div.chart-body.chart');
  if (!data.length) {
    wrap.append(h('div.chart-empty', emptyText));
    return wrap;
  }
  if (legend) wrap.append(h('div.chart-legend', legend.map(([label, c]) => h('span', h('span.swatch', { style: { background: c } }), label))));
  wrap.append(tableView(title, data, valueLabel));
  const max = Math.max(...data.map(([, v]) => v), 1);
  return responsive(wrap, (width) => {
    const labelW = 72;
    const valueW = 34;
    const pad = { top: 4, bottom: 4 };
    const height = pad.top + pad.bottom + rowHeight * data.length;
    const iw = width - labelW - valueW;
    const svg = svgEl('svg', { viewBox: `0 0 ${width} ${height}`, width, height, role: 'img', 'aria-label': title });
    const bh = 12;
    data.forEach(([label, value], i) => {
      const y = pad.top + rowHeight * i + (rowHeight - bh) / 2;
      const w = Math.max(value > 0 ? 3 : 0, (value / max) * iw);
      const g = svgEl('g', { class: 'bar-group', tabindex: '0', role: 'listitem' });
      tooltip(g, `${label}: ${value} ${valueLabel.toLowerCase()}`);
      g.append(svgEl('rect', { class: 'hit', x: 0, y: pad.top + rowHeight * i, width, height: rowHeight }));
      const cat = svgEl('text', { x: 0, y: y + bh - 1, 'text-anchor': 'start', fill: 'var(--text)' });
      cat.textContent = label;
      g.append(cat);
      g.append(svgEl('rect', { class: 'track', x: labelW, y, width: iw, height: bh, rx: 4 }));
      g.append(svgEl('path', { class: 'bar', d: roundedRightRect(labelW, y, w, bh, 4), fill: color(label) }));
      const v = svgEl('text', { class: 'value-label', x: width, y: y + bh - 1, 'text-anchor': 'end' });
      v.textContent = value;
      g.append(v);
      svg.append(g);
    });
    return svg;
  });
}
