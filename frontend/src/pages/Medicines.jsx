import React, { useEffect, useState, useCallback } from 'react';
import {
  Plus,
  Search,
  Filter,
  Edit2,
  Trash2,
  CheckCircle,
  Eye,
  Pill,
  Layers,
} from 'lucide-react';
import { medicineService } from '../services/medicineService';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { canDeactivateMedicine } from '../utils/permissions';
import { DataTable } from '../components/common/DataTable';
import { StatusBadge } from '../components/common/StatusBadge';
import { Button } from '../components/common/Button';
import { Modal } from '../components/common/Modal';
import { ConfirmDialog } from '../components/common/ConfirmDialog';
import { formatDate } from '../utils/date';
import { formatCurrency } from '../utils/currency';

export function Medicines() {
  const { user } = useAuth();
  const { success, error } = useToast();

  const [medicines, setMedicines] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [size] = useState(10);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const [search, setSearch] = useState('');
  const [activeFilter, setActiveFilter] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('ALL');

  // Modals state
  const [modalOpen, setModalOpen] = useState(false);
  const [editingMedicine, setEditingMedicine] = useState(null);
  const [formName, setFormName] = useState('');
  const [formDesc, setFormDesc] = useState('');
  const [formCategory, setFormCategory] = useState('');
  const [formThreshold, setFormThreshold] = useState(10);
  const [saving, setSaving] = useState(false);

  // Deactivate confirmation
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [deactivatingMedicine, setDeactivatingMedicine] = useState(null);
  const [deactivatingAction, setDeactivatingAction] = useState('deactivate'); // 'deactivate' or 'activate'
  const [processingStatus, setProcessingStatus] = useState(false);

  // View Batches Modal
  const [viewBatchesOpen, setViewBatchesOpen] = useState(false);
  const [selectedMedicineBatches, setSelectedMedicineBatches] = useState(null);
  const [loadingBatches, setLoadingBatches] = useState(false);

  const fetchMedicines = useCallback(async () => {
    try {
      setLoading(true);
      const activeParam = activeFilter === 'active' ? true : activeFilter === 'inactive' ? false : null;
      const res = await medicineService.getAll({
        page,
        size,
        search,
        active: activeParam,
      });

      let items = res?.content || [];
      if (categoryFilter !== 'ALL') {
        items = items.filter((m) => m.category === categoryFilter);
      }

      setMedicines(items);
      setTotalPages(res?.totalPages || 0);
      setTotalElements(res?.totalElements || 0);
    } catch (err) {
      error(err.message || 'Failed to fetch medicines');
    } finally {
      setLoading(false);
    }
  }, [page, size, search, activeFilter, categoryFilter, error]);

  useEffect(() => {
    const timer = setTimeout(() => {
      fetchMedicines();
    }, 250);
    return () => clearTimeout(timer);
  }, [fetchMedicines]);

  // Handle open Add/Edit
  const handleOpenAdd = () => {
    setEditingMedicine(null);
    setFormName('');
    setFormDesc('');
    setFormCategory('');
    setFormThreshold(10);
    setModalOpen(true);
  };

  const handleOpenEdit = (medicine) => {
    setEditingMedicine(medicine);
    setFormName(medicine.name);
    setFormDesc(medicine.description || '');
    setFormCategory(medicine.category || '');
    setFormThreshold(medicine.lowStockThreshold || 10);
    setModalOpen(true);
  };

  // Submit Add/Edit
  const handleSaveMedicine = async (e) => {
    e.preventDefault();
    if (!formName.trim()) {
      error('Medicine name is required');
      return;
    }

    try {
      setSaving(true);
      const payload = {
        name: formName.trim(),
        description: formDesc.trim(),
        category: formCategory.trim() || 'General',
        lowStockThreshold: parseInt(formThreshold, 10) || 10,
      };

      if (editingMedicine) {
        await medicineService.update(editingMedicine.id, payload);
        success(`Medicine "${payload.name}" updated successfully`);
      } else {
        await medicineService.create(payload);
        success(`Medicine "${payload.name}" created successfully`);
      }
      setModalOpen(false);
      fetchMedicines();
    } catch (err) {
      error(err.message || 'Error saving medicine');
    } finally {
      setSaving(false);
    }
  };

  // Handle Deactivate/Reactivate
  const handleToggleActivePrompt = (medicine, action) => {
    setDeactivatingMedicine(medicine);
    setDeactivatingAction(action);
    setConfirmOpen(true);
  };

  const handleConfirmToggleActive = async () => {
    if (!deactivatingMedicine) return;
    try {
      setProcessingStatus(true);
      if (deactivatingAction === 'deactivate') {
        await medicineService.deactivate(deactivatingMedicine.id);
        success(`Medicine "${deactivatingMedicine.name}" has been deactivated`);
      } else {
        await medicineService.activate(deactivatingMedicine.id);
        success(`Medicine "${deactivatingMedicine.name}" has been reactivated`);
      }
      setConfirmOpen(false);
      fetchMedicines();
    } catch (err) {
      error(err.message || 'Error updating status');
    } finally {
      setProcessingStatus(false);
    }
  };

  // Handle View Batches
  const handleViewBatches = async (medicine) => {
    try {
      setViewBatchesOpen(true);
      setLoadingBatches(true);
      const res = await medicineService.getByName(medicine.name);
      setSelectedMedicineBatches(res);
    } catch (err) {
      error('Could not load batch details');
    } finally {
      setLoadingBatches(false);
    }
  };

  const columns = [
    {
      key: 'name',
      title: 'Medicine Name',
      render: (m) => (
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-xl bg-teal-50 border border-teal-200/80 flex items-center justify-center text-teal-700 shrink-0">
            <Pill className="w-4 h-4" />
          </div>
          <div>
            <p className="font-semibold text-slate-800">{m.name}</p>
            <p className="text-xs text-slate-400 line-clamp-1">{m.description || 'No description'}</p>
          </div>
        </div>
      ),
    },
    {
      key: 'category',
      title: 'Category',
      render: (m) => (
        <span className="inline-block px-2.5 py-1 rounded-lg text-xs font-medium bg-slate-100 text-slate-700">
          {m.category || 'General'}
        </span>
      ),
    },
    {
      key: 'totalStock',
      title: 'Available Stock',
      render: (m) => {
        const isCritical = m.totalStock === 0;
        const isLow = m.totalStock <= m.lowStockThreshold;
        return (
          <div>
            <p className={`font-bold ${isCritical ? 'text-rose-600' : isLow ? 'text-amber-600' : 'text-slate-800'}`}>
              {m.totalStock.toLocaleString('en-IN')} units
            </p>
            <p className="text-[11px] text-slate-400">Min threshold: {m.lowStockThreshold}</p>
          </div>
        );
      },
    },
    {
      key: 'status',
      title: 'Status',
      render: (m) => <StatusBadge status={m.active ? 'ACTIVE' : 'INACTIVE'} />,
    },
    {
      key: 'actions',
      title: 'Actions',
      align: 'right',
      render: (m) => (
        <div className="flex items-center justify-end gap-1.5">
          <button
            onClick={() => handleViewBatches(m)}
            className="p-1.5 rounded-lg text-slate-500 hover:text-teal-700 hover:bg-slate-100 transition-colors"
            title="View Batches"
          >
            <Eye className="w-4 h-4" />
          </button>

          <button
            onClick={() => handleOpenEdit(m)}
            className="p-1.5 rounded-lg text-slate-500 hover:text-teal-700 hover:bg-slate-100 transition-colors"
            title="Edit Medicine"
          >
            <Edit2 className="w-4 h-4" />
          </button>

          {canDeactivateMedicine(user) && (
            m.active ? (
              <button
                onClick={() => handleToggleActivePrompt(m, 'deactivate')}
                className="p-1.5 rounded-lg text-slate-400 hover:text-rose-600 hover:bg-rose-50 transition-colors"
                title="Deactivate Medicine (Admin)"
              >
                <Trash2 className="w-4 h-4" />
              </button>
            ) : (
              <button
                onClick={() => handleToggleActivePrompt(m, 'activate')}
                className="p-1.5 rounded-lg text-slate-400 hover:text-emerald-600 hover:bg-emerald-50 transition-colors"
                title="Reactivate Medicine (Admin)"
              >
                <CheckCircle className="w-4 h-4" />
              </button>
            )
          )}
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-xl sm:text-2xl font-bold text-slate-800 tracking-tight">Medicine Catalog</h1>
          <p className="text-xs sm:text-sm text-slate-500 mt-1">
            Manage formulations, stock thresholds, categories, and batch associations.
          </p>
        </div>
        <Button variant="primary" icon={Plus} onClick={handleOpenAdd}>
          Add Medicine
        </Button>
      </div>

      {/* Filter and Search Bar */}
      <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-sm flex flex-col md:flex-row gap-3 items-center justify-between">
        <div className="relative w-full md:w-80">
          <Search className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search medicine name..."
            className="w-full pl-9 pr-4 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-teal-600 focus:border-transparent"
          />
        </div>

        <div className="flex flex-wrap items-center gap-2.5 w-full md:w-auto">
          {/* Status Filter */}
          <select
            value={activeFilter}
            onChange={(e) => { setActiveFilter(e.target.value); setPage(0); }}
            className="px-3 py-2 rounded-xl border border-slate-200 text-xs font-medium text-slate-700 bg-white focus:outline-none focus:ring-2 focus:ring-teal-600"
          >
            <option value="">All Statuses</option>
            <option value="active">Active Only</option>
            <option value="inactive">Inactive Only</option>
          </select>

          {/* Category Filter */}
          <select
            value={categoryFilter}
            onChange={(e) => { setCategoryFilter(e.target.value); setPage(0); }}
            className="px-3 py-2 rounded-xl border border-slate-200 text-xs font-medium text-slate-700 bg-white focus:outline-none focus:ring-2 focus:ring-teal-600"
          >
            <option value="ALL">All Categories</option>
            <option value="Pain Relief">Pain Relief</option>
            <option value="Antibiotics">Antibiotics</option>
            <option value="Allergy">Allergy</option>
            <option value="Diabetes">Diabetes</option>
            <option value="General">General</option>
          </select>
        </div>
      </div>

      {/* Medicines Table */}
      <DataTable
        columns={columns}
        data={medicines}
        loading={loading}
        emptyTitle="No medicines found"
        emptyMessage="Try adjusting your search or category filters, or register a new medicine."
        emptyAction={
          <Button variant="primary" size="sm" icon={Plus} onClick={handleOpenAdd}>
            Add First Medicine
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

      {/* Add / Edit Modal */}
      <Modal
        isOpen={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editingMedicine ? 'Edit Medicine' : 'Add New Medicine'}
        subtitle={editingMedicine ? `Updating formulation for ${editingMedicine.name}` : 'Register a new medicine in the catalog'}
      >
        <form onSubmit={handleSaveMedicine} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">
              Medicine Name *
            </label>
            <input
              type="text"
              required
              value={formName}
              onChange={(e) => setFormName(e.target.value)}
              placeholder="e.g. Paracetamol 500mg"
              className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-teal-600"
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">
                Category
              </label>
              <input
                type="text"
                value={formCategory}
                onChange={(e) => setFormCategory(e.target.value)}
                placeholder="e.g. Pain Relief"
                className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-teal-600"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">
                Low Stock Threshold
              </label>
              <input
                type="number"
                min="0"
                value={formThreshold}
                onChange={(e) => setFormThreshold(e.target.value)}
                className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-teal-600"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">
              Description / Notes
            </label>
            <textarea
              rows="3"
              value={formDesc}
              onChange={(e) => setFormDesc(e.target.value)}
              placeholder="Dosage instructions, contraindications or packaging notes..."
              className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-teal-600"
            />
          </div>

          <div className="pt-4 border-t border-slate-100 flex items-center justify-end gap-3">
            <Button variant="secondary" onClick={() => setModalOpen(false)} disabled={saving}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={saving}>
              {editingMedicine ? 'Update Medicine' : 'Create Medicine'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Confirmation Dialog for Deactivation / Activation */}
      <ConfirmDialog
        isOpen={confirmOpen}
        onClose={() => setConfirmOpen(false)}
        onConfirm={handleConfirmToggleActive}
        title={deactivatingAction === 'deactivate' ? 'Deactivate Medicine?' : 'Reactivate Medicine?'}
        message={
          deactivatingAction === 'deactivate'
            ? `Deactivating "${deactivatingMedicine?.name}" will prevent pharmacists from selling it or adding new batches to it.`
            : `Reactivating "${deactivatingMedicine?.name}" will restore it to the active dispensing catalog.`
        }
        confirmText={deactivatingAction === 'deactivate' ? 'Deactivate' : 'Reactivate'}
        confirmVariant={deactivatingAction === 'deactivate' ? 'danger' : 'success'}
        loading={processingStatus}
      />

      {/* View Batches Drawer Modal */}
      <Modal
        isOpen={viewBatchesOpen}
        onClose={() => setViewBatchesOpen(false)}
        title={`Batches for ${selectedMedicineBatches?.medicineName || 'Medicine'}`}
        subtitle={`Total non-expired stock: ${selectedMedicineBatches?.totalQuantity || 0} units (sorted by FEFO)`}
        maxWidth="max-w-2xl"
      >
        {loadingBatches ? (
          <div className="py-8 text-center text-sm text-slate-400">Loading batch details...</div>
        ) : !selectedMedicineBatches?.batches || selectedMedicineBatches.batches.length === 0 ? (
          <div className="py-8 text-center text-sm text-slate-400">
            No batches exist for this medicine yet. Add a batch in the Batch Management page.
          </div>
        ) : (
          <div className="space-y-3">
            {selectedMedicineBatches.batches.map((batch, idx) => (
              <div
                key={batch.batchId || idx}
                className="p-4 rounded-xl border border-slate-200 bg-slate-50/50 flex items-center justify-between"
              >
                <div className="flex items-center gap-3">
                  <div className="w-8 h-8 rounded-lg bg-teal-100 text-teal-800 flex items-center justify-center font-bold text-xs">
                    #{idx + 1}
                  </div>
                  <div>
                    <p className="text-sm font-bold text-slate-800">{batch.batchNumber}</p>
                    <p className="text-xs text-slate-500">
                      Expiry: {formatDate(batch.expiryDate)}{' '}
                      {idx === 0 && !batch.expired && (
                        <span className="text-[10px] bg-teal-100 text-teal-700 font-bold px-1.5 py-0.5 rounded ml-1">
                          FEFO First Out
                        </span>
                      )}
                    </p>
                  </div>
                </div>
                <div className="text-right">
                  <p className="text-sm font-bold text-slate-800">{batch.quantity} units</p>
                  <StatusBadge status={batch.expired ? 'EXPIRED' : 'ACTIVE'} size="sm" />
                </div>
              </div>
            ))}
          </div>
        )}
      </Modal>
    </div>
  );
}
