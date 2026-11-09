import React, { useEffect, useState } from 'react';
import {
  BarChart3,
  TrendingUp,
  Receipt,
  DollarSign,
  Package,
  RotateCcw,
  RefreshCw,
  Award,
  Percent,
} from 'lucide-react';
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  PieChart,
  Pie,
  Cell,
  Legend,
  AreaChart,
  Area,
} from 'recharts';
import { reportService } from '../services/reportService';
import { batchService } from '../services/batchService';
import { formatCurrency } from '../utils/currency';
import { Button } from '../components/common/Button';

export function Reports() {
  const [loading, setLoading] = useState(true);

  const [summary, setSummary] = useState({
    netRevenue: 0,
    costOfGoodsSold: 0,
    grossProfit: 0,
    grossMarginPercentage: 0,
    totalUnitsSold: 0,
    totalSalesCount: 0,
    totalReturnsCount: 0,
    totalRefundAmount: 0,
    topMedicines: [],
    revenueTrends: [],
  });

  const [stockHealthData, setStockHealthData] = useState([]);

  const fetchReports = async () => {
    try {
      setLoading(true);

      const [reportRes, batchesRes] = await Promise.all([
        reportService.getSummary(),
        batchService.getAll({ size: 100 }),
      ]);

      setSummary(reportRes || {
        netRevenue: 0,
        costOfGoodsSold: 0,
        grossProfit: 0,
        grossMarginPercentage: 0,
        totalUnitsSold: 0,
        totalSalesCount: 0,
        totalReturnsCount: 0,
        totalRefundAmount: 0,
        topMedicines: [],
        revenueTrends: [],
      });

      // Stock Health distribution
      const batches = batchesRes?.content || [];
      let activeStock = 0;
      let lowStock = 0;
      let expiredStock = 0;

      batches.forEach((b) => {
        if (b.expired) {
          expiredStock += b.quantity;
        } else if (b.quantity <= 15) {
          lowStock += b.quantity;
        } else {
          activeStock += b.quantity;
        }
      });

      setStockHealthData([
        { name: 'Optimal Stock', value: activeStock || 1, color: '#0d9488' },
        { name: 'Low Stock Batches', value: lowStock || 0, color: '#f59e0b' },
        { name: 'Expired in Storage', value: expiredStock || 0, color: '#e11d48' },
      ]);
    } catch (err) {
      console.error('Error generating reports:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchReports();
  }, []);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-xl sm:text-2xl font-bold text-slate-800 tracking-tight">Reports & Profitability</h1>
          <p className="text-xs sm:text-sm text-slate-500 mt-1">
            Historical net revenue, cost of goods sold (COGS), gross margin, and stock risk analytics using sale-time cost prices.
          </p>
        </div>
        <Button variant="secondary" size="sm" icon={RefreshCw} onClick={fetchReports} loading={loading}>
          Recalculate
        </Button>
      </div>

      {/* Primary KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-sm">
          <p className="text-xs font-semibold uppercase text-slate-400">Net Revenue</p>
          <h3 className="text-2xl font-extrabold text-teal-800 mt-1">
            {formatCurrency(summary.netRevenue)}
          </h3>
          <p className="text-[11px] text-slate-500 mt-0.5">{summary.totalSalesCount} transactions billed</p>
        </div>

        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-sm">
          <p className="text-xs font-semibold uppercase text-slate-400">Cost of Goods Sold (COGS)</p>
          <h3 className="text-2xl font-extrabold text-slate-700 mt-1">
            {formatCurrency(summary.costOfGoodsSold)}
          </h3>
          <p className="text-[11px] text-slate-500 mt-0.5">Sale-time batch purchase cost</p>
        </div>

        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-sm">
          <p className="text-xs font-semibold uppercase text-slate-400">Gross Profit</p>
          <h3 className="text-2xl font-extrabold text-emerald-600 mt-1">
            {formatCurrency(summary.grossProfit)}
          </h3>
          <p className="text-[11px] text-slate-500 mt-0.5">
            Gross Margin: <strong className="text-emerald-700">{summary.grossMarginPercentage?.toFixed(1)}%</strong>
          </p>
        </div>

        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-sm">
          <p className="text-xs font-semibold uppercase text-slate-400">Sales Returns & Refunds</p>
          <h3 className="text-2xl font-extrabold text-purple-700 mt-1">
            {formatCurrency(summary.totalRefundAmount)}
          </h3>
          <p className="text-[11px] text-slate-500 mt-0.5">{summary.totalReturnsCount} processed returns</p>
        </div>
      </div>

      {/* Revenue & Profit Trends Chart */}
      {summary.revenueTrends && summary.revenueTrends.length > 0 && (
        <div className="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-sm">
          <div className="flex items-center gap-2 mb-4">
            <TrendingUp className="w-5 h-5 text-teal-700" />
            <div>
              <h3 className="text-base font-bold text-slate-800">Historical Revenue & Gross Profit Trend</h3>
              <p className="text-xs text-slate-500">Realized revenue vs gross profit calculated from frozen sale-time cost</p>
            </div>
          </div>
          <div className="h-64 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={summary.revenueTrends}>
                <defs>
                  <linearGradient id="colorNetRevenue" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#0f766e" stopOpacity={0.4} />
                    <stop offset="95%" stopColor="#0f766e" stopOpacity={0.0} />
                  </linearGradient>
                  <linearGradient id="colorGrossProfit" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#059669" stopOpacity={0.4} />
                    <stop offset="95%" stopColor="#059669" stopOpacity={0.0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f1f5f9" />
                <XAxis dataKey="date" tick={{ fontSize: 11, fill: '#64748b' }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fontSize: 11, fill: '#64748b' }} axisLine={false} tickLine={false} />
                <Tooltip
                  formatter={(val, name) => [
                    formatCurrency(val),
                    name === 'netRevenue' ? 'Net Revenue' : 'Gross Profit',
                  ]}
                  contentStyle={{ borderRadius: '12px', border: '1px solid #e2e8f0' }}
                />
                <Area type="monotone" dataKey="netRevenue" stroke="#0f766e" fillOpacity={1} fill="url(#colorNetRevenue)" />
                <Area type="monotone" dataKey="grossProfit" stroke="#059669" fillOpacity={1} fill="url(#colorGrossProfit)" />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>
      )}

      {/* Charts Section: Top Medicines and Stock Health */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Top Selling Medicines */}
        <div className="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-sm">
          <div className="flex items-center gap-2 mb-4">
            <Award className="w-5 h-5 text-teal-700" />
            <div>
              <h3 className="text-base font-bold text-slate-800">Top-Selling Formulations</h3>
              <p className="text-xs text-slate-500">Highest volume medicines dispensed</p>
            </div>
          </div>

          <div className="h-64 w-full">
            {summary.topMedicines && summary.topMedicines.length > 0 ? (
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={summary.topMedicines}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f1f5f9" />
                  <XAxis dataKey="medicineName" tick={{ fontSize: 11, fill: '#64748b' }} axisLine={false} tickLine={false} />
                  <YAxis tick={{ fontSize: 11, fill: '#64748b' }} axisLine={false} tickLine={false} />
                  <Tooltip
                    formatter={(val, name) => [
                      name === 'netUnitsSold' ? `${val} Units` : formatCurrency(val),
                      name === 'netUnitsSold' ? 'Units Sold' : 'Gross Profit',
                    ]}
                    contentStyle={{ borderRadius: '12px', border: '1px solid #e2e8f0' }}
                  />
                  <Bar dataKey="netUnitsSold" fill="#0f766e" radius={[6, 6, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            ) : (
              <div className="flex items-center justify-center h-full text-xs text-slate-400">
                No sales volume recorded yet.
              </div>
            )}
          </div>
        </div>

        {/* Stock Health Breakdown Pie Chart */}
        <div className="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-sm">
          <div className="flex items-center gap-2 mb-4">
            <Package className="w-5 h-5 text-teal-700" />
            <div>
              <h3 className="text-base font-bold text-slate-800">Inventory Health Breakdown</h3>
              <p className="text-xs text-slate-500">Optimal vs Low-Stock vs Expired Stock in Storage</p>
            </div>
          </div>

          <div className="h-64 w-full flex items-center justify-center">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={stockHealthData}
                  cx="50%"
                  cy="50%"
                  innerRadius={60}
                  outerRadius={85}
                  paddingAngle={4}
                  dataKey="value"
                >
                  {stockHealthData.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={entry.color} />
                  ))}
                </Pie>
                <Tooltip
                  formatter={(val) => [`${val} Units`, 'Quantity']}
                  contentStyle={{ borderRadius: '12px', border: '1px solid #e2e8f0' }}
                />
                <Legend iconType="circle" wrapperStyle={{ fontSize: '12px' }} />
              </PieChart>
            </ResponsiveContainer>
          </div>
        </div>
      </div>

      {/* Top Medicine Profitability Table */}
      {summary.topMedicines && summary.topMedicines.length > 0 && (
        <div className="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-sm overflow-hidden">
          <h3 className="text-base font-bold text-slate-800 mb-1">Product-Level Profitability Ledger</h3>
          <p className="text-xs text-slate-500 mb-4">Accurate historical profit per medicine accounting for customer returns and actual sale-time batch cost</p>
          <div className="overflow-x-auto">
            <table className="w-full text-xs text-left">
              <thead>
                <tr className="border-b border-slate-100 text-slate-400 uppercase tracking-wider">
                  <th className="py-2.5 px-3">Medicine</th>
                  <th className="py-2.5 px-3">Category</th>
                  <th className="py-2.5 px-3 text-right">Net Units</th>
                  <th className="py-2.5 px-3 text-right">Net Revenue</th>
                  <th className="py-2.5 px-3 text-right">COGS</th>
                  <th className="py-2.5 px-3 text-right">Gross Profit</th>
                  <th className="py-2.5 px-3 text-right">Gross Margin</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {summary.topMedicines.map((m) => (
                  <tr key={m.medicineId} className="hover:bg-slate-50/50">
                    <td className="py-2.5 px-3 font-semibold text-slate-800">{m.medicineName}</td>
                    <td className="py-2.5 px-3 text-slate-500">{m.category || 'General'}</td>
                    <td className="py-2.5 px-3 text-right font-medium">{m.netUnitsSold}</td>
                    <td className="py-2.5 px-3 text-right font-medium">{formatCurrency(m.netRevenue)}</td>
                    <td className="py-2.5 px-3 text-right text-slate-600">{formatCurrency(m.costOfGoodsSold)}</td>
                    <td className="py-2.5 px-3 text-right font-bold text-emerald-600">{formatCurrency(m.grossProfit)}</td>
                    <td className="py-2.5 px-3 text-right font-semibold text-slate-700">{m.grossMarginPercentage?.toFixed(1)}%</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
