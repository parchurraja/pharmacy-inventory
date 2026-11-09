import React, { useEffect, useState } from 'react';
import {
  AlertTriangle,
  Clock,
  Skull,
  Trash2,
  Calendar,
  Layers,
  Pill,
  ArrowRight,
  ShieldCheck,
  CheckCircle2,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import { inventoryService } from '../services/inventoryService';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { canDisposeExpired } from '../utils/permissions';
import { formatDate } from '../utils/date';
import { formatCurrency } from '../utils/currency';
import { StatusBadge } from '../components/common/StatusBadge';
import { Button } from '../components/common/Button';
import { ConfirmDialog } from '../components/common/ConfirmDialog';

export function Alerts() {
  const { user } = useAuth();
  const { success, error } = useToast();

  const [activeTab, setActiveTab] = useState('low-stock'); // 'low-stock' | 'expiring' | 'expired'
  const [expiryDays, setExpiryDays] = useState(30);

  const [loading, setLoading] = useState(true);
  const [lowStockAlerts, setLowStockAlerts] = useState([]);
  const [expiringBatches, setExpiringBatches] = useState([]);
  const [expiredBatches, setExpiredBatches] = useState([]);

  // Dispose Modal
  const [disposeConfirmOpen, setDisposeConfirmOpen] = useState(false);
  const [batchToDispose, setBatchToDispose] = useState(null);
  const [disposing, setDisposing] = useState(false);

  const fetchAlerts = async () => {
    try {
      setLoading(true);
      const [lowStockRes, expiringRes, expiredRes] = await Promise.all([
        inventoryService.getLowStockAlerts(),
        inventoryService.getExpiringBatches(expiryDays),
        inventoryService.getExpiredBatches(),
      ]);

      setLowStockAlerts(lowStockRes || []);
      setExpiringBatches(expiringRes || []);
      setExpiredBatches(expiredRes || []);
    } catch (err) {
      error('Failed to load inventory alerts');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAlerts();
  }, [expiryDays]);

  // Handle Dispose Action
  const handlePromptDispose = (batch) => {
    if (!canDisposeExpired(user)) {
      error('Only Administrators have permission to write off expired inventory.');
      return;
    }
    setBatchToDispose(batch);
    setDisposeConfirmOpen(true);
  };

  const handleConfirmDispose = async () => {
    if (!batchToDispose) return;
    try {
      setDisposing(true);
      await inventoryService.disposeExpiredBatch(batchToDispose.id);
      success(`Expired batch ${batchToDispose.batchNumber} has been safely written off and zeroed out.`);
      setDisposeConfirmOpen(false);
      fetchAlerts();
    } catch (err) {
      error(err.message || 'Error disposing expired batch');
    } finally {
      setDisposing(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-xl sm:text-2xl font-bold text-slate-800 tracking-tight">Inventory Alerts & Safety</h1>
        <p className="text-xs sm:text-sm text-slate-500 mt-1">
          Monitor safety thresholds, proactive expiry horizons (30/60/90 days), and expired stock write-offs.
        </p>
      </div>

      {/* Tab Navigation */}
      <div className="flex border-b border-slate-200 gap-2">
        <button
          onClick={() => setActiveTab('low-stock')}
          className={`pb-3 px-4 text-sm font-semibold flex items-center gap-2 border-b-2 transition-colors ${
            activeTab === 'low-stock'
              ? 'border-teal-700 text-teal-700'
              : 'border-transparent text-slate-500 hover:text-slate-800'
          }`}
        >
          <AlertTriangle className="w-4 h-4 text-amber-500" />
          Low-Stock Warnings
          <span className="text-xs px-2 py-0.5 rounded-full bg-amber-100 text-amber-800 font-bold">
            {lowStockAlerts.length}
          </span>
        </button>

        <button
          onClick={() => setActiveTab('expiring')}
          className={`pb-3 px-4 text-sm font-semibold flex items-center gap-2 border-b-2 transition-colors ${
            activeTab === 'expiring'
              ? 'border-teal-700 text-teal-700'
              : 'border-transparent text-slate-500 hover:text-slate-800'
          }`}
        >
          <Clock className="w-4 h-4 text-teal-600" />
          Expiring Soon
          <span className="text-xs px-2 py-0.5 rounded-full bg-teal-100 text-teal-800 font-bold">
            {expiringBatches.length}
          </span>
        </button>

        <button
          onClick={() => setActiveTab('expired')}
          className={`pb-3 px-4 text-sm font-semibold flex items-center gap-2 border-b-2 transition-colors ${
            activeTab === 'expired'
              ? 'border-teal-700 text-teal-700'
              : 'border-transparent text-slate-500 hover:text-slate-800'
          }`}
        >
          <Skull className="w-4 h-4 text-rose-500" />
          Expired in Storage
          <span className="text-xs px-2 py-0.5 rounded-full bg-rose-100 text-rose-800 font-bold">
            {expiredBatches.length}
          </span>
        </button>
      </div>

      {/* Tab Content */}
      <div className="bg-white rounded-2xl border border-slate-200/80 shadow-sm p-6">
        {/* Tab 1: Low-Stock Warnings */}
        {activeTab === 'low-stock' && (
          <div className="space-y-4">
            <div className="flex justify-between items-center pb-3 border-b border-slate-100">
              <h3 className="text-sm font-bold text-slate-800">Medicines Below Safety Threshold</h3>
              <span className="text-xs text-slate-400">Restock batches before stock completely exhausts</span>
            </div>

            {loading ? (
              <div className="py-8 text-center text-sm text-slate-400">Loading alerts...</div>
            ) : lowStockAlerts.length === 0 ? (
              <div className="py-12 text-center text-slate-400 text-sm flex flex-col items-center">
                <CheckCircle2 className="w-10 h-10 text-emerald-500 mb-2" />
                <p className="font-semibold text-slate-700">Healthy Stock Levels</p>
                <p className="text-xs text-slate-400 mt-0.5">All medicines currently satisfy minimum stock requirements.</p>
              </div>
            ) : (
              <div className="divide-y divide-slate-100">
                {lowStockAlerts.map((item) => {
                  const isOut = item.currentStock === 0;
                  return (
                    <div key={item.medicineId} className="py-4 flex items-center justify-between gap-4">
                      <div className="flex items-center gap-3.5">
                        <div className={`w-9 h-9 rounded-xl flex items-center justify-center shrink-0 ${
                          isOut ? 'bg-rose-100 text-rose-700' : 'bg-amber-100 text-amber-700'
                        }`}>
                          <Pill className="w-5 h-5" />
                        </div>
                        <div>
                          <p className="font-bold text-slate-800 text-sm">{item.medicineName}</p>
                          <p className="text-xs text-slate-400">{item.category || 'General'}</p>
                        </div>
                      </div>

                      <div className="flex items-center gap-4">
                        <div className="text-right">
                          <p className={`text-sm font-bold ${isOut ? 'text-rose-600' : 'text-amber-600'}`}>
                            {item.currentStock} units left
                          </p>
                          <p className="text-[11px] text-slate-400">Threshold: {item.lowStockThreshold}</p>
                        </div>
                        <StatusBadge
                          status={isOut ? 'CRITICAL' : 'WARNING'}
                          label={isOut ? 'Out of Stock' : 'Low Stock'}
                        />
                        <Link to="/batches">
                          <Button variant="secondary" size="sm" icon={ArrowRight}>Restock</Button>
                        </Link>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}

        {/* Tab 2: Expiring Soon Batches */}
        {activeTab === 'expiring' && (
          <div className="space-y-4">
            <div className="flex flex-col sm:flex-row justify-between sm:items-center gap-3 pb-3 border-b border-slate-100">
              <div>
                <h3 className="text-sm font-bold text-slate-800">Batches Approaching Expiration</h3>
                <p className="text-xs text-slate-400">FEFO priority moves these batches to the front of dispensing</p>
              </div>

              {/* Time Horizon Filter */}
              <div className="flex items-center gap-1.5 text-xs font-semibold">
                <span className="text-slate-400 mr-1">Time Horizon:</span>
                {[30, 60, 90].map((days) => (
                  <button
                    key={days}
                    onClick={() => setExpiryDays(days)}
                    className={`px-3 py-1 rounded-xl transition-colors ${
                      expiryDays === days
                        ? 'bg-teal-700 text-white'
                        : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                    }`}
                  >
                    {days} Days
                  </button>
                ))}
              </div>
            </div>

            {loading ? (
              <div className="py-8 text-center text-sm text-slate-400">Loading expiring batches...</div>
            ) : expiringBatches.length === 0 ? (
              <div className="py-12 text-center text-slate-400 text-sm flex flex-col items-center">
                <CheckCircle2 className="w-10 h-10 text-emerald-500 mb-2" />
                <p className="font-semibold text-slate-700">No Imminent Expirations</p>
                <p className="text-xs text-slate-400 mt-0.5">No batches expire within the next {expiryDays} days.</p>
              </div>
            ) : (
              <div className="divide-y divide-slate-100">
                {expiringBatches.map((batch, idx) => (
                  <div key={idx} className="py-4 flex items-center justify-between gap-4">
                    <div className="flex items-center gap-3.5">
                      <div className="w-9 h-9 rounded-xl bg-teal-50 border border-teal-200/80 flex items-center justify-center text-teal-700 shrink-0">
                        <Layers className="w-5 h-5" />
                      </div>
                      <div>
                        <p className="font-bold text-slate-800 text-sm">{batch.medicineName}</p>
                        <p className="text-xs text-slate-500 font-mono">
                          Batch: <strong>{batch.batchNumber}</strong> • Expiry: {formatDate(batch.expiryDate)}
                        </p>
                      </div>
                    </div>

                    <div className="flex items-center gap-4">
                      <div className="text-right">
                        <p className="text-sm font-bold text-slate-800">{batch.quantity} units</p>
                        <p className="text-[11px] text-amber-600 font-bold">{batch.daysUntilExpiry} days remaining</p>
                      </div>
                      <StatusBadge status="WARNING" label="Expiring Soon" />
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* Tab 3: Expired Stock in Storage */}
        {activeTab === 'expired' && (
          <div className="space-y-4">
            <div className="flex justify-between items-center pb-3 border-b border-slate-100">
              <div>
                <h3 className="text-sm font-bold text-slate-800">Expired Stock Requiring Disposal</h3>
                <p className="text-xs text-slate-400">
                  Expired stock is blocked from sale by backend FEFO rules. Admins can write off and safely dispose.
                </p>
              </div>
              {!canDisposeExpired(user) && (
                <span className="text-xs text-amber-600 font-medium flex items-center gap-1 bg-amber-50 px-2.5 py-1 rounded-lg border border-amber-200">
                  <ShieldCheck className="w-3.5 h-3.5" />
                  Disposal requires Admin role
                </span>
              )}
            </div>

            {loading ? (
              <div className="py-8 text-center text-sm text-slate-400">Loading expired inventory...</div>
            ) : expiredBatches.length === 0 ? (
              <div className="py-12 text-center text-slate-400 text-sm flex flex-col items-center">
                <CheckCircle2 className="w-10 h-10 text-emerald-500 mb-2" />
                <p className="font-semibold text-slate-700">Zero Expired Stock</p>
                <p className="text-xs text-slate-400 mt-0.5">No expired batches remain in physical inventory.</p>
              </div>
            ) : (
              <div className="divide-y divide-slate-100">
                {expiredBatches.map((batch) => (
                  <div key={batch.id} className="py-4 flex items-center justify-between gap-4">
                    <div className="flex items-center gap-3.5">
                      <div className="w-9 h-9 rounded-xl bg-rose-50 border border-rose-200 flex items-center justify-center text-rose-700 shrink-0">
                        <Skull className="w-5 h-5" />
                      </div>
                      <div>
                        <p className="font-bold text-slate-800 text-sm">{batch.medicineName}</p>
                        <p className="text-xs text-slate-500 font-mono">
                          Batch: <strong>{batch.batchNumber}</strong> • Expired on: {formatDate(batch.expiryDate)}
                        </p>
                      </div>
                    </div>

                    <div className="flex items-center gap-4">
                      <div className="text-right">
                        <p className="text-sm font-bold text-rose-700">{batch.quantity} units to dispose</p>
                        <StatusBadge status="EXPIRED" size="sm" />
                      </div>

                      {canDisposeExpired(user) ? (
                        <Button
                          variant="danger"
                          size="sm"
                          icon={Trash2}
                          onClick={() => handlePromptDispose(batch)}
                        >
                          Dispose & Write Off
                        </Button>
                      ) : (
                        <Button
                          variant="secondary"
                          size="sm"
                          disabled
                          title="Requires Admin privileges"
                        >
                          Admin Only
                        </Button>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      {/* Confirm Disposal Dialog */}
      <ConfirmDialog
        isOpen={disposeConfirmOpen}
        onClose={() => setDisposeConfirmOpen(false)}
        onConfirm={handleConfirmDispose}
        title="Confirm Stock Disposal & Write-off"
        message={`Are you sure you want to dispose of batch "${batchToDispose?.batchNumber}" (${batchToDispose?.quantity} units)? This will zero out the batch inventory and create an EXPIRED_DISPOSAL audit record in the database.`}
        confirmText="Confirm Disposal"
        confirmVariant="danger"
        loading={disposing}
      />
    </div>
  );
}
