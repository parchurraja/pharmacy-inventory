import React, { useEffect, useState, useCallback } from 'react';
import {
  Plus,
  Search,
  Layers,
  AlertTriangle,
  SlidersHorizontal,
  Calendar,
  CheckCircle,
  Clock,
  Pill,
} from 'lucide-react';
import { batchService } from '../services/batchService';
import { medicineService } from '../services/medicineService';
import { inventoryService } from '../services/inventoryService';
import { useToast } from '../context/ToastContext';
import { DataTable } from '../components/common/DataTable';
import { StatusBadge } from '../components/common/StatusBadge';
import { Button } from '../components/common/Button';
import { Modal } from '../components/common/Modal';
import { formatDate, getDaysUntil } from '../utils/date';
import { formatCurrency } from '../utils/currency';

export function Batches() {
  const { success, error } = useToast();

  const [batches, setBatches] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [size] = useState(10);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [medicinesList, setMedicinesList] = useState([]);

  // Filter state
  const [activeFilter, setActiveFilter] = useState('');
  const [sortField, setSortField] = useState('expiryDate,asc');

  // Add Batch Modal
  const [addModalOpen, setAddModalOpen] = useState(false);
  const [addMedicineName, setAddMedicineName] = useState('');
  const [addBatchNumber, setAddBatchNumber] = useState('');
  const [addQuantity, setAddQuantity] = useState(50);
  const [addExpiryDate, setAddExpiryDate] = useState('');
  const [addCostPrice, setAddCostPrice] = useState(2.50);
  const [addSellingPrice, setAddSellingPrice] = useState(5.00);
  const [submittingAdd, setSubmittingAdd] = useState(false);

  // Stock Adjustment Modal
  const [adjustModalOpen, setAdjustModalOpen] = useState(false);
  const [selectedBatch, setSelectedBatch] = useState(null);
  const [adjustType, setAdjustType] = useState('DAMAGE');
  const [adjustQty, setAdjustQty] = useState(1);
  const [adjustReason, setAdjustReason] = useState('');
  const [submittingAdjust, setSubmittingAdjust] = useState(false);

  // Fetch medicines for dropdown
  useEffect(() => {
    async function loadMedicines() {
      try {
        const res = await medicineService.getAll({ size: 100, active: true });
        setMedicinesList(res?.content || []);
      } catch {
        // Silently handle
      }
    }
    loadMedicines();
  }, []);

  const fetchBatches = useCallback(async () => {
    try {
      setLoading(true);
      const activeParam = activeFilter === 'active' ? true : activeFilter === 'inactive' ? false : null;
      const res = await batchService.getAll({
        page,
        size,
        sort: sortField,
        active: activeParam,
      });

      setBatches(res?.content || []);
      setTotalPages(res?.totalPages || 0);
      setTotalElements(res?.totalElements || 0);
    } catch (err) {
      error(err.message || 'Failed to load batches');
    } finally {
      setLoading(false);
    }
  }, [page, size, sortField, activeFilter, error]);

  useEffect(() => {
    fetchBatches();
  }, [fetchBatches]);

  // Open Add Batch
  const handleOpenAdd = () => {
    setAddMedicineName(medicinesList[0]?.name || '');
    setAddBatchNumber('BATCH-' + Math.floor(1000 + Math.random() * 9000));
    setAddQuantity(50);
    // Default expiry 1 year in future
    const nextYear = new Date();
    nextYear.setFullYear(nextYear.getFullYear() + 1);
    setAddExpiryDate(nextYear.toISOString().split('T')[0]);
    setAddCostPrice(3.00);
    setAddSellingPrice(6.00);
    setAddModalOpen(true);
  };

  const handleSaveBatch = async (e) => {
    e.preventDefault();
    if (!addMedicineName.trim() || !addBatchNumber.trim() || !addExpiryDate) {
      error('Please complete all required fields.');
      return;
    }

    try {
      setSubmittingAdd(true);
      await batchService.create({
        medicineName: addMedicineName.trim(),
        batchNumber: addBatchNumber.trim(),
        quantity: parseInt(addQuantity, 10),
        expiryDate: addExpiryDate,
        costPrice: parseFloat(addCostPrice) || 0,
        sellingPrice: parseFloat(addSellingPrice) || 0,
      });
      success(`Batch ${addBatchNumber} created successfully!`);
      setAddModalOpen(false);
      fetchBatches();
    } catch (err) {
      error(err.message || 'Error creating batch');
    } finally {
      setSubmittingAdd(false);
    }
  };

  // Open Adjustment
  const handleOpenAdjust = (batch) => {
    setSelectedBatch(batch);
    setAdjustType('DAMAGE');
    setAdjustQty(1);
    setAdjustReason('Packaging damaged during handling');
    setAdjustModalOpen(true);
  };

  const handleSaveAdjustment = async (e) => {
    e.preventDefault();
    if (!selectedBatch) return;

    try {
      setSubmittingAdjust(true);
      const qtyNum = parseInt(adjustQty, 10);
      // For damage, quantityAdjusted is negative
      const signedQty = adjustType === 'DAMAGE' || adjustType === 'EXPIRED_DISPOSAL' ? -Math.abs(qtyNum) : qtyNum;

      await inventoryService.adjustStock({
        batchId: selectedBatch.id,
        adjustmentType: adjustType,
        quantityAdjusted: signedQty,
        reason: adjustReason.trim() || `Manual adjustment: ${adjustType}`,
      });

      success(`Stock adjusted for batch ${selectedBatch.batchNumber}`);
      setAdjustModalOpen(false);
      fetchBatches();
    } catch (err) {
      error(err.message || 'Failed to adjust stock');
    } finally {
      setSubmittingAdjust(false);
    }
  };

  const columns = [
    {
      key: 'batchNumber',
      title: 'Batch Number',
      render: (b) => (
        <div className="flex items-center gap-2.5">
          <div className="w-8 h-8 rounded-xl bg-slate-100 flex items-center justify-center text-slate-600 shrink-0">
            <Layers className="w-4 h-4" />
          </div>
          <div>
            <span className="font-mono font-bold text-slate-800 text-xs sm:text-sm">{b.batchNumber}</span>
            <p className="text-[11px] text-slate-400">ID: #{b.id}</p>
          </div>
        </div>
      ),
    },
    {
      key: 'medicineName',
      title: 'Medicine',
      render: (b) => (
        <div className="font-semibold text-slate-800">
          {b.medicineName}
        </div>
      ),
    },
    {
      key: 'quantity',
      title: 'Stock Quantity',
      render: (b) => (
        <div>
          <span className={`font-bold ${b.quantity === 0 ? 'text-slate-400' : 'text-slate-800'}`}>
            {b.quantity} units
          </span>
          {b.quantity === 0 && <span className="text-[10px] text-slate-400 block">(Depleted)</span>}
        </div>
      ),
    },
    {
      key: 'expiryDate',
      title: 'Expiry Date (FEFO)',
      render: (b) => {
        const days = getDaysUntil(b.expiryDate);
        const isPast = days <= 0;
        const isExpiringSoon = days > 0 && days <= 60;

        return (
          <div>
            <div className="flex items-center gap-1.5">
              <Calendar className="w-3.5 h-3.5 text-slate-400" />
              <span className={`text-xs font-semibold ${isPast ? 'text-rose-600' : isExpiringSoon ? 'text-amber-600' : 'text-slate-700'}`}>
                {formatDate(b.expiryDate)}
              </span>
            </div>
            <p className={`text-[10px] mt-0.5 font-medium ${isPast ? 'text-rose-500 font-bold' : isExpiringSoon ? 'text-amber-600' : 'text-slate-400'}`}>
              {isPast ? 'Expired' : `${days} days remaining`}
            </p>
          </div>
        );
      },
    },
    {
      key: 'pricing',
      title: 'Price (Cost / Sell)',
      render: (b) => (
        <div className="text-xs">
          <p className="text-slate-500 font-medium">Cost: {formatCurrency(b.costPrice || 0)}</p>
          <p className="font-bold text-teal-800">Sell: {formatCurrency(b.sellingPrice || 0)}</p>
        </div>
      ),
    },
    {
      key: 'status',
      title: 'Batch Status',
      render: (b) => {
        if (b.expired) return <StatusBadge status="EXPIRED" />;
        const days = getDaysUntil(b.expiryDate);
        if (days <= 60) return <StatusBadge status="EXPIRING_SOON" label="Expiring Soon" />;
        return <StatusBadge status={b.active ? 'ACTIVE' : 'INACTIVE'} />;
      },
    },
    {
      key: 'actions',
      title: 'Actions',
      align: 'right',
      render: (b) => (
        <div className="flex items-center justify-end gap-1.5">
          <Button
            variant="secondary"
            size="sm"
            icon={SlidersHorizontal}
            onClick={() => handleOpenAdjust(b)}
            disabled={b.quantity === 0 && b.expired}
          >
            Adjust
          </Button>
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-xl sm:text-2xl font-bold text-slate-800 tracking-tight">Batch Management</h1>
          <p className="text-xs sm:text-sm text-slate-500 mt-1">
            Track batch expiration, cost/selling prices, FEFO priorities, and inventory adjustments.
          </p>
        </div>
        <Button variant="primary" icon={Plus} onClick={handleOpenAdd}>
          Add Batch
        </Button>
      </div>

      {/* Control Bar */}
      <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-sm flex flex-col md:flex-row gap-3 items-center justify-between">
        <div className="flex items-center gap-2">
          <span className="text-xs font-semibold uppercase text-slate-400">Sort by:</span>
          <select
            value={sortField}
            onChange={(e) => { setSortField(e.target.value); setPage(0); }}
            className="px-3 py-1.5 rounded-xl border border-slate-200 text-xs font-medium text-slate-700 bg-white focus:outline-none focus:ring-2 focus:ring-teal-600"
          >
            <option value="expiryDate,asc">Expiry Date (FEFO Earliest First)</option>
            <option value="expiryDate,desc">Expiry Date (Latest First)</option>
            <option value="quantity,desc">Highest Stock Quantity</option>
            <option value="quantity,asc">Lowest Stock Quantity</option>
          </select>
        </div>

        <div className="flex items-center gap-2 w-full md:w-auto">
          <select
            value={activeFilter}
            onChange={(e) => { setActiveFilter(e.target.value); setPage(0); }}
            className="px-3 py-1.5 rounded-xl border border-slate-200 text-xs font-medium text-slate-700 bg-white focus:outline-none focus:ring-2 focus:ring-teal-600"
          >
            <option value="">All Batches</option>
            <option value="active">Active Only</option>
            <option value="inactive">Inactive Only</option>
          </select>
        </div>
      </div>

      {/* Batches Table */}
      <DataTable
        columns={columns}
        data={batches}
        loading={loading}
        emptyTitle="No batches found"
        emptyMessage="Add a new batch to replenish pharmacy medicine stock."
        emptyAction={
          <Button variant="primary" size="sm" icon={Plus} onClick={handleOpenAdd}>
            Add First Batch
          </Button>
        }
        pagination={{
          page,
          size,
          totalPages,
          totalElements,
          onPageChange: (newPage) => setPage(newPage),
        }}
      />

      {/* Add Batch Modal */}
      <Modal
        isOpen={addModalOpen}
        onClose={() => setAddModalOpen(false)}
        title="Add Medicine Batch"
        subtitle="Record intake of a new manufacturing batch into inventory"
      >
        <form onSubmit={handleSaveBatch} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">
              Medicine Formulation *
            </label>
            <input
              type="text"
              required
              list="medicines-suggestions"
              value={addMedicineName}
              onChange={(e) => setAddMedicineName(e.target.value)}
              placeholder="Search or type medicine name..."
              className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-teal-600"
            />
            <datalist id="medicines-suggestions">
              {medicinesList.map((m) => (
                <option key={m.id} value={m.name} />
              ))}
            </datalist>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">
                Batch Number *
              </label>
              <input
                type="text"
                required
                value={addBatchNumber}
                onChange={(e) => setAddBatchNumber(e.target.value)}
                placeholder="e.g. PARA-BATCH-004"
                className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-teal-600"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">
                Quantity (Units) *
              </label>
              <input
                type="number"
                min="1"
                required
                value={addQuantity}
                onChange={(e) => setAddQuantity(e.target.value)}
                className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-teal-600"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">
              Expiry Date *
            </label>
            <input
              type="date"
              required
              value={addExpiryDate}
              onChange={(e) => setAddExpiryDate(e.target.value)}
              className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-teal-600"
            />
            {addExpiryDate && getDaysUntil(addExpiryDate) <= 0 && (
              <p className="text-xs text-amber-600 mt-1 font-medium flex items-center gap-1">
                <AlertTriangle className="w-3.5 h-3.5" />
                Note: This date is in the past. Batch will be logged as expired.
              </p>
            )}
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">
                Cost Price (₹)
              </label>
              <input
                type="number"
                step="0.01"
                min="0"
                value={addCostPrice}
                onChange={(e) => setAddCostPrice(e.target.value)}
                className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-teal-600"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">
                Selling Price (₹) *
              </label>
              <input
                type="number"
                step="0.01"
                min="0"
                required
                value={addSellingPrice}
                onChange={(e) => setAddSellingPrice(e.target.value)}
                className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 text-sm font-semibold focus:outline-none focus:ring-2 focus:ring-teal-600 text-teal-800"
              />
            </div>
          </div>

          <div className="pt-4 border-t border-slate-100 flex items-center justify-end gap-3">
            <Button variant="secondary" onClick={() => setAddModalOpen(false)} disabled={submittingAdd}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={submittingAdd}>
              Create Batch
            </Button>
          </div>
        </form>
      </Modal>

      {/* Stock Adjustment Modal */}
      <Modal
        isOpen={adjustModalOpen}
        onClose={() => setAdjustModalOpen(false)}
        title="Stock Adjustment"
        subtitle={`Adjust inventory for batch ${selectedBatch?.batchNumber} (${selectedBatch?.medicineName})`}
      >
        <form onSubmit={handleSaveAdjustment} className="space-y-4">
          <div className="p-3 bg-slate-50 rounded-xl border border-slate-200 text-xs flex justify-between">
            <span className="text-slate-500">Current available quantity:</span>
            <span className="font-bold text-slate-800">{selectedBatch?.quantity} units</span>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">
              Adjustment Type
            </label>
            <select
              value={adjustType}
              onChange={(e) => setAdjustType(e.target.value)}
              className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-teal-600"
            >
              <option value="DAMAGE">Damaged Goods (Deduct stock)</option>
              <option value="MANUAL_CORRECTION">Manual Count Correction</option>
              <option value="EXPIRED_DISPOSAL">Expired Stock Disposal (Deduct stock)</option>
            </select>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">
              Quantity to Adjust
            </label>
            <input
              type="number"
              min="1"
              max={selectedBatch?.quantity || 9999}
              required
              value={adjustQty}
              onChange={(e) => setAdjustQty(e.target.value)}
              className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-teal-600"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">
              Reason / Justification *
            </label>
            <textarea
              rows="3"
              required
              value={adjustReason}
              onChange={(e) => setAdjustReason(e.target.value)}
              placeholder="e.g. Water damage during transport, count audit correction..."
              className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-teal-600"
            />
          </div>

          <div className="pt-4 border-t border-slate-100 flex items-center justify-end gap-3">
            <Button variant="secondary" onClick={() => setAdjustModalOpen(false)} disabled={submittingAdjust}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={submittingAdjust}>
              Submit Adjustment
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
