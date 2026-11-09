import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Pill,
  Package,
  AlertTriangle,
  Clock,
  TrendingUp,
  Receipt,
  ArrowRight,
  RefreshCw,
  ShoppingCart
} from 'lucide-react';
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  BarChart,
  Bar
} from 'recharts';
import { medicineService } from '../services/medicineService';
import { batchService } from '../services/batchService';
import { salesService } from '../services/salesService';
import { inventoryService } from '../services/inventoryService';
import { formatCurrency } from '../utils/currency';
import { formatDate, formatDateTime } from '../utils/date';
import { StatusBadge } from '../components/common/StatusBadge';
import { Button } from '../components/common/Button';

export function Dashboard() {
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState({
    totalMedicines: 0,
    totalUnits: 0,
    lowStockCount: 0,
    expiringCount: 0,
    expiredCount: 0,
    todayRevenue: 0,
    todaySalesCount: 0,
  });

  const [recentSales, setRecentSales] = useState([]);
  const [urgentLowStock, setUrgentLowStock] = useState([]);
  const [salesTrendData, setSalesTrendData] = useState([]);
  const [categoryData, setCategoryData] = useState([]);

  const fetchDashboardData = async () => {
    try {
      setLoading(true);

      const [
        medicinesRes,
        batchesRes,
        salesRes,
        lowStockRes,
        expiringRes,
        expiredRes
      ] = await Promise.all([
        medicineService.getAll({ size: 100 }),
        batchService.getAll({ size: 100 }),
        salesService.getHistory({ size: 50, sort: 'saleDate,desc' }),
        inventoryService.getLowStockAlerts(),
        inventoryService.getExpiringBatches(30),
        inventoryService.getExpiredBatches()
      ]);

      const medicines = medicinesRes?.content || [];
      const batches = batchesRes?.content || [];
      const sales = salesRes?.content || [];
      const lowStock = lowStockRes || [];
      const expiring = expiringRes || [];
      const expired = expiredRes || [];

      // Calculate total available non-expired units
      const totalUnits = batches
        .filter(b => b.active && !b.expired)
        .reduce((sum, b) => sum + (b.quantity || 0), 0);

      // Calculate today's sales
      const todayStr = new Date().toISOString().split('T')[0];
      const todaySales = sales.filter(s => s.saleDate && s.saleDate.startsWith(todayStr));
      const todayRevenue = todaySales.reduce((sum, s) => sum + parseFloat(s.totalAmount || 0), 0);

      setStats({
        totalMedicines: medicinesRes?.totalElements || medicines.length,
        totalUnits,
        lowStockCount: lowStock.length,
        expiringCount: expiring.length,
        expiredCount: expired.length,
        todayRevenue,
        todaySalesCount: todaySales.length,
      });

      setRecentSales(sales.slice(0, 5));
      setUrgentLowStock(lowStock.slice(0, 5));

      // Build real 7-day sales trend from actual sales history
      const daysMap = {};
      const dayNames = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
      for (let i = 6; i >= 0; i--) {
        const d = new Date();
        d.setDate(d.getDate() - i);
        const key = d.toISOString().split('T')[0];
        const label = dayNames[d.getDay()];
        daysMap[key] = { name: label, date: key, revenue: 0, salesCount: 0 };
      }

      sales.forEach(s => {
        if (s.saleDate) {
          const dateKey = s.saleDate.split('T')[0];
          if (daysMap[dateKey]) {
            daysMap[dateKey].revenue += parseFloat(s.totalAmount || 0);
            daysMap[dateKey].salesCount += 1;
          }
        }
      });
      setSalesTrendData(Object.values(daysMap));

      // Category breakdown
      const catCount = {};
      medicines.forEach(m => {
        const cat = m.category || 'General';
        catCount[cat] = (catCount[cat] || 0) + (m.totalStock || 0);
      });
      const catChart = Object.keys(catCount).map(name => ({
        name,
        stock: catCount[name]
      })).slice(0, 6);
      setCategoryData(catChart);

    } catch (err) {
      console.error('Error fetching dashboard data:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDashboardData();
  }, []);

  return (
    <div className="space-y-6">
      {/* Top Banner & Refresh */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 bg-gradient-to-r from-teal-800 to-teal-900 rounded-2xl p-6 text-white shadow-sm">
        <div>
          <h2 className="text-xl sm:text-2xl font-bold tracking-tight">Operations Overview</h2>
          <p className="text-teal-200 text-xs sm:text-sm mt-1">
            Real-time pharmacy inventory metrics, FEFO batch allocations, and live sales revenue.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <Button
            variant="secondary"
            size="sm"
            onClick={fetchDashboardData}
            loading={loading}
            icon={RefreshCw}
            className="bg-white/10 text-white border-white/20 hover:bg-white/20"
          >
            Refresh Data
          </Button>
          <Link to="/sales">
            <Button variant="success" size="sm" icon={ShoppingCart}>
              New POS Dispense
            </Button>
          </Link>
        </div>
      </div>

      {/* KPI Cards Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Total Medicines */}
        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-sm flex items-center justify-between">
          <div>
            <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">Medicines</p>
            <h3 className="text-2xl font-bold text-slate-800 mt-1">
              {loading ? '...' : stats.totalMedicines}
            </h3>
            <p className="text-xs text-slate-500 mt-0.5">Active catalog items</p>
          </div>
          <div className="w-12 h-12 rounded-xl bg-teal-50 border border-teal-100 text-teal-700 flex items-center justify-center shrink-0">
            <Pill className="w-6 h-6" />
          </div>
        </div>

        {/* Available Units */}
        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-sm flex items-center justify-between">
          <div>
            <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">Available Stock</p>
            <h3 className="text-2xl font-bold text-slate-800 mt-1">
              {loading ? '...' : stats.totalUnits.toLocaleString('en-IN')}
            </h3>
            <p className="text-xs text-slate-500 mt-0.5">Valid unexpired units</p>
          </div>
          <div className="w-12 h-12 rounded-xl bg-emerald-50 border border-emerald-100 text-emerald-700 flex items-center justify-center shrink-0">
            <Package className="w-6 h-6" />
          </div>
        </div>

        {/* Low Stock Alert */}
        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-sm flex items-center justify-between">
          <div>
            <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">Low Stock Alert</p>
            <h3 className="text-2xl font-bold text-amber-600 mt-1">
              {loading ? '...' : stats.lowStockCount}
            </h3>
            <p className="text-xs text-slate-500 mt-0.5">Below threshold</p>
          </div>
          <div className="w-12 h-12 rounded-xl bg-amber-50 border border-amber-100 text-amber-600 flex items-center justify-center shrink-0">
            <AlertTriangle className="w-6 h-6" />
          </div>
        </div>

        {/* Expiring Batches */}
        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-sm flex items-center justify-between">
          <div>
            <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">Expiring Soon</p>
            <h3 className="text-2xl font-bold text-rose-600 mt-1">
              {loading ? '...' : stats.expiringCount}
            </h3>
            <p className="text-xs text-slate-500 mt-0.5">Expiring in 30 days</p>
          </div>
          <div className="w-12 h-12 rounded-xl bg-rose-50 border border-rose-100 text-rose-600 flex items-center justify-center shrink-0">
            <Clock className="w-6 h-6" />
          </div>
        </div>
      </div>

      {/* Charts Section */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Revenue Trend Area Chart */}
        <div className="lg:col-span-2 bg-white p-6 rounded-2xl border border-slate-200/80 shadow-sm">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h3 className="text-base font-bold text-slate-800">7-Day Sales & Revenue Trend</h3>
              <p className="text-xs text-slate-500 mt-0.5">Live transactions recorded in database</p>
            </div>
            <div className="text-right">
              <span className="text-xs text-slate-400">Today's Sales</span>
              <p className="text-base font-bold text-teal-700">{formatCurrency(stats.todayRevenue)}</p>
            </div>
          </div>

          <div className="h-64 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={salesTrendData}>
                <defs>
                  <linearGradient id="colorRev" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#0f766e" stopOpacity={0.3}/>
                    <stop offset="95%" stopColor="#0f766e" stopOpacity={0}/>
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f1f5f9" />
                <XAxis dataKey="name" tick={{ fontSize: 12, fill: '#64748b' }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fontSize: 12, fill: '#64748b' }} axisLine={false} tickLine={false} tickFormatter={(val) => `₹${val}`} />
                <Tooltip
                  formatter={(value) => [formatCurrency(value), 'Revenue']}
                  labelFormatter={(label, payload) => payload?.[0]?.payload?.date || label}
                  contentStyle={{ borderRadius: '12px', border: '1px solid #e2e8f0', boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)' }}
                />
                <Area type="monotone" dataKey="revenue" stroke="#0f766e" strokeWidth={2.5} fillOpacity={1} fill="url(#colorRev)" />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Stock Breakdown by Category Bar Chart */}
        <div className="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-sm">
          <div className="mb-4">
            <h3 className="text-base font-bold text-slate-800">Stock by Category</h3>
            <p className="text-xs text-slate-500 mt-0.5">Unit distribution across catalog</p>
          </div>

          <div className="h-64 w-full">
            {categoryData.length > 0 ? (
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={categoryData} layout="vertical">
                  <CartesianGrid strokeDasharray="3 3" horizontal={false} stroke="#f1f5f9" />
                  <XAxis type="number" tick={{ fontSize: 11, fill: '#64748b' }} axisLine={false} tickLine={false} />
                  <YAxis dataKey="name" type="category" width={80} tick={{ fontSize: 11, fill: '#64748b' }} axisLine={false} tickLine={false} />
                  <Tooltip
                    formatter={(val) => [`${val} Units`, 'Available Stock']}
                    contentStyle={{ borderRadius: '12px', border: '1px solid #e2e8f0' }}
                  />
                  <Bar dataKey="stock" fill="#14b8a6" radius={[0, 6, 6, 0]} />
                </BarChart>
              </ResponsiveContainer>
            ) : (
              <div className="flex items-center justify-center h-full text-xs text-slate-400">
                No category data available
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Dual Tables: Urgent Low Stock & Recent Transactions */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Urgent Low Stock Alert Table */}
        <div className="bg-white rounded-2xl border border-slate-200/80 shadow-sm overflow-hidden flex flex-col">
          <div className="px-6 py-4 border-b border-slate-100 flex items-center justify-between bg-slate-50/50">
            <div className="flex items-center gap-2">
              <AlertTriangle className="w-4 h-4 text-amber-500" />
              <h3 className="text-sm font-bold text-slate-800">Urgent Low-Stock Items</h3>
            </div>
            <Link to="/alerts" className="text-xs font-semibold text-teal-700 hover:text-teal-800 flex items-center gap-1">
              View all ({stats.lowStockCount}) <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>

          <div className="divide-y divide-slate-100 flex-1">
            {urgentLowStock.length === 0 ? (
              <div className="p-8 text-center text-xs text-slate-400">
                All medicines are currently above their low-stock thresholds.
              </div>
            ) : (
              urgentLowStock.map((item) => (
                <div key={item.medicineId} className="px-6 py-3.5 flex items-center justify-between hover:bg-slate-50/60 transition-colors">
                  <div>
                    <p className="text-sm font-semibold text-slate-800">{item.medicineName}</p>
                    <p className="text-xs text-slate-400">{item.category || 'General'}</p>
                  </div>
                  <div className="flex items-center gap-3">
                    <div className="text-right">
                      <p className={`text-sm font-bold ${item.currentStock === 0 ? 'text-rose-600' : 'text-amber-600'}`}>
                        {item.currentStock} units
                      </p>
                      <p className="text-[10px] text-slate-400">Min: {item.lowStockThreshold}</p>
                    </div>
                    <Link to="/batches">
                      <Button variant="secondary" size="sm">Restock</Button>
                    </Link>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        {/* Recent Transactions Table */}
        <div className="bg-white rounded-2xl border border-slate-200/80 shadow-sm overflow-hidden flex flex-col">
          <div className="px-6 py-4 border-b border-slate-100 flex items-center justify-between bg-slate-50/50">
            <div className="flex items-center gap-2">
              <Receipt className="w-4 h-4 text-teal-600" />
              <h3 className="text-sm font-bold text-slate-800">Recent Transactions</h3>
            </div>
            <Link to="/sales-history" className="text-xs font-semibold text-teal-700 hover:text-teal-800 flex items-center gap-1">
              View history <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>

          <div className="divide-y divide-slate-100 flex-1">
            {recentSales.length === 0 ? (
              <div className="p-8 text-center text-xs text-slate-400">
                No transactions recorded yet. Complete a sale in the POS interface.
              </div>
            ) : (
              recentSales.map((sale) => (
                <div key={sale.id} className="px-6 py-3.5 flex items-center justify-between hover:bg-slate-50/60 transition-colors">
                  <div>
                    <p className="text-sm font-semibold text-slate-800">{sale.invoiceNumber}</p>
                    <p className="text-xs text-slate-400">
                      {sale.customerName || 'Walk-in'} • {formatDateTime(sale.saleDate)}
                    </p>
                  </div>
                  <div className="flex items-center gap-3">
                    <div className="text-right">
                      <p className="text-sm font-bold text-slate-800">{formatCurrency(sale.totalAmount)}</p>
                      <StatusBadge status={sale.status} size="sm" />
                    </div>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
