import React, { useEffect, useState, useCallback } from 'react';
import {
  Receipt,
  Search,
  Eye,
  RotateCcw,
  Calendar,
  Layers,
  CheckCircle,
  Clock,
  Printer,
} from 'lucide-react';
import { salesService } from '../services/salesService';
import { useToast } from '../context/ToastContext';
import { DataTable } from '../components/common/DataTable';
import { StatusBadge } from '../components/common/StatusBadge';
import { Button } from '../components/common/Button';
import { Modal } from '../components/common/Modal';
import { formatCurrency } from '../utils/currency';
import { formatDate, formatDateTime } from '../utils/date';

export function SalesHistory() {
  const { success, error } = useToast();

  const [sales, setSales] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [size] = useState(10);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const [searchInvoice, setSearchInvoice] = useState('');

  // View Details Modal
  const [detailModalOpen, setDetailModalOpen] = useState(false);
  const [selectedSale, setSelectedSale] = useState(null);

  // Return Modal
  const [returnModalOpen, setReturnModalOpen] = useState(false);
  const [returnItemQuantities, setReturnItemQuantities] = useState({});
  const [returnReason, setReturnReason] = useState('Customer return of unopened medicine');
  const [submittingReturn, setSubmittingReturn] = useState(false);

  const fetchSalesHistory = useCallback(async () => {
    try {
      setLoading(true);
      const res = await salesService.getHistory({
        page,
        size,
        sort: 'saleDate,desc',
      });

      let items = res?.content || [];
      if (searchInvoice.trim()) {
        items = items.filter((s) =>
          s.invoiceNumber.toLowerCase().includes(searchInvoice.toLowerCase().trim())
        );
      }

      setSales(items);
      setTotalPages(res?.totalPages || 0);
      setTotalElements(res?.totalElements || 0);
    } catch (err) {
      error(err.message || 'Failed to load sales history');
    } finally {
      setLoading(false);
    }
  }, [page, size, searchInvoice, error]);

  useEffect(() => {
    fetchSalesHistory();
  }, [fetchSalesHistory]);

  const handleOpenDetails = (sale) => {
    setSelectedSale(sale);
    setDetailModalOpen(true);
  };

  const handleOpenReturnModal = (sale) => {
    setSelectedSale(sale);
    // Initialize return quantities to 0
    const initialQtyMap = {};
    sale.items.forEach((item) => {
      initialQtyMap[item.id] = 0;
    });
    setReturnItemQuantities(initialQtyMap);
    setReturnReason('Customer return of unopened medicine');
    setReturnModalOpen(true);
  };

  const handleReturnQtyChange = (itemId, maxAvailable, value) => {
    const qty = parseInt(value, 10) || 0;
    if (qty > maxAvailable) {
      error(`Cannot return more than ${maxAvailable} available units for this item.`);
      return;
    }
    setReturnItemQuantities((prev) => ({
      ...prev,
      [itemId]: Math.max(0, qty),
    }));
  };

  // Submit Sales Return
  const handleSubmitReturn = async (e) => {
    e.preventDefault();
    if (!selectedSale) return;

    const returnItems = Object.entries(returnItemQuantities)
      .filter(([_, qty]) => qty > 0)
      .map(([saleItemId, qty]) => ({
        saleItemId: parseInt(saleItemId, 10),
        quantity: qty,
      }));

    if (returnItems.length === 0) {
      error('Please specify at least 1 unit to return.');
      return;
    }

    try {
      setSubmittingReturn(true);
      const res = await salesService.processReturn(selectedSale.id, {
        items: returnItems,
        reason: returnReason.trim() || 'Customer return',
      });

      success(res.message || 'Sales return processed successfully!');
      setReturnModalOpen(false);
      setDetailModalOpen(false);
      fetchSalesHistory();
    } catch (err) {
      error(err.message || 'Error processing sales return');
    } finally {
      setSubmittingReturn(false);
    }
  };

  // Calculate live refund total
  const estimatedRefund = selectedSale
    ? selectedSale.items.reduce((sum, item) => {
        const qty = returnItemQuantities[item.id] || 0;
        return sum + qty * parseFloat(item.unitPrice || 0);
      }, 0)
    : 0;

  const columns = [
    {
      key: 'invoiceNumber',
      title: 'Invoice Number',
      render: (s) => (
        <div className="flex items-center gap-2.5">
          <div className="w-8 h-8 rounded-xl bg-teal-50 border border-teal-200/80 flex items-center justify-center text-teal-700 shrink-0">
            <Receipt className="w-4 h-4" />
          </div>
          <div>
            <span className="font-mono font-bold text-slate-800 text-xs sm:text-sm">{s.invoiceNumber}</span>
            <p className="text-[11px] text-slate-400">{s.items?.length || 0} items dispensed</p>
          </div>
        </div>
      ),
    },
    {
      key: 'saleDate',
      title: 'Date & Time',
      render: (s) => (
        <div className="text-xs text-slate-600">
          <p className="font-medium text-slate-800">{formatDateTime(s.saleDate)}</p>
        </div>
      ),
    },
    {
      key: 'customerName',
      title: 'Customer',
      render: (s) => (
        <div className="text-xs">
          <p className="font-semibold text-slate-800">{s.customerName || 'Walk-in'}</p>
          <p className="text-slate-400">By: {s.soldBy || 'pharmacist'}</p>
        </div>
      ),
    },
    {
      key: 'totalAmount',
      title: 'Total Amount',
      render: (s) => (
        <span className="font-bold text-slate-800 text-sm">
          {formatCurrency(s.totalAmount)}
        </span>
      ),
    },
    {
      key: 'status',
      title: 'Status',
      render: (s) => <StatusBadge status={s.status} />,
    },
    {
      key: 'actions',
      title: 'Actions',
      align: 'right',
      render: (s) => (
        <div className="flex items-center justify-end gap-1.5">
          <Button
            variant="secondary"
            size="sm"
            icon={Eye}
            onClick={() => handleOpenDetails(s)}
          >
            Details
          </Button>

          {s.status !== 'REFUNDED' && (
            <Button
              variant="outline"
              size="sm"
              icon={RotateCcw}
              onClick={() => handleOpenReturnModal(s)}
            >
              Return
            </Button>
          )}
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-xl sm:text-2xl font-bold text-slate-800 tracking-tight">Sales History</h1>
          <p className="text-xs sm:text-sm text-slate-500 mt-1">
            Complete transaction ledger with batch traceability, itemized pricing, and sales returns.
          </p>
        </div>
      </div>

      {/* Search Bar */}
      <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-sm flex items-center justify-between">
        <div className="relative w-full sm:w-80">
          <Search className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none" />
          <input
            type="text"
            value={searchInvoice}
            onChange={(e) => setSearchInvoice(e.target.value)}
            placeholder="Search invoice number..."
            className="w-full pl-9 pr-4 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-teal-600 focus:border-transparent"
          />
        </div>
      </div>

      {/* Sales Table */}
      <DataTable
        columns={columns}
        data={sales}
        loading={loading}
        emptyTitle="No transactions found"
        emptyMessage="Completed sales from the POS dispensing interface will appear here."
        pagination={{
          page,
          size,
          totalPages,
          totalElements,
          onPageChange: (newPage) => setPage(newPage),
        }}
      />

      {/* View Sale Details Modal */}
      <Modal
        isOpen={detailModalOpen}
        onClose={() => setDetailModalOpen(false)}
        title={`Invoice ${selectedSale?.invoiceNumber}`}
        subtitle={`Dispensed to ${selectedSale?.customerName} on ${formatDateTime(selectedSale?.saleDate)}`}
        maxWidth="max-w-xl"
      >
        {selectedSale && (
          <div className="space-y-4">
            <div className="grid grid-cols-3 gap-3 p-4 bg-slate-50 rounded-xl border border-slate-200 text-xs">
              <div>
                <span className="text-slate-400">Total Billed:</span>
                <p className="font-bold text-slate-800 text-sm mt-0.5">{formatCurrency(selectedSale.totalAmount)}</p>
              </div>
              <div>
                <span className="text-slate-400">Status:</span>
                <div className="mt-0.5"><StatusBadge status={selectedSale.status} size="sm" /></div>
              </div>
              <div>
                <span className="text-slate-400">Pharmacist:</span>
                <p className="font-semibold text-slate-800 mt-0.5">{selectedSale.soldBy}</p>
              </div>
            </div>

            <div>
              <h4 className="text-xs font-bold uppercase text-slate-500 mb-2">Itemized Batch Allocations</h4>
              <div className="divide-y divide-slate-100 border border-slate-200 rounded-xl overflow-hidden text-xs">
                {selectedSale.items?.map((item) => {
                  const returnable = item.quantity - (item.returnedQuantity || 0);
                  return (
                    <div key={item.id} className="p-3 bg-white flex justify-between items-center">
                      <div>
                        <p className="font-bold text-slate-800 text-sm">{item.medicineName}</p>
                        <p className="text-slate-500 flex items-center gap-1 mt-0.5 font-mono">
                          <Layers className="w-3 h-3 text-teal-600" />
                          Batch: {item.batchNumber} • Expiry: {formatDate(item.expiryDate)}
                        </p>
                        {item.returnedQuantity > 0 && (
                          <span className="text-[10px] text-amber-700 bg-amber-50 px-1.5 py-0.5 rounded font-semibold mt-1 inline-block">
                            {item.returnedQuantity} units returned
                          </span>
                        )}
                      </div>
                      <div className="text-right">
                        <p className="font-semibold text-slate-700">{item.quantity} × {formatCurrency(item.unitPrice)}</p>
                        <p className="font-bold text-teal-800 text-sm">{formatCurrency(item.subtotal)}</p>
                        <p className="text-[10px] text-slate-400 mt-0.5">Avail. to return: {returnable}</p>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>

            <div className="pt-4 border-t border-slate-100 flex items-center justify-between">
              {selectedSale.status !== 'REFUNDED' && (
                <Button
                  variant="outline"
                  size="sm"
                  icon={RotateCcw}
                  onClick={() => {
                    setDetailModalOpen(false);
                    handleOpenReturnModal(selectedSale);
                  }}
                >
                  Process Sales Return
                </Button>
              )}
              <Button
                variant="secondary"
                size="sm"
                onClick={() => setDetailModalOpen(false)}
                className="ml-auto"
              >
                Close
              </Button>
            </div>
          </div>
        )}
      </Modal>

      {/* Process Sales Return Modal */}
      <Modal
        isOpen={returnModalOpen}
        onClose={() => setReturnModalOpen(false)}
        title="Process Sales Return"
        subtitle={`Return eligible quantities to inventory for invoice ${selectedSale?.invoiceNumber}`}
        maxWidth="max-w-lg"
      >
        {selectedSale && (
          <form onSubmit={handleSubmitReturn} className="space-y-4">
            <p className="text-xs text-slate-500">
              Select the returned quantities below. Stock will be restored to original batches and a financial refund will be logged.
            </p>

            <div className="space-y-3">
              {selectedSale.items?.map((item) => {
                const maxAvailable = item.quantity - (item.returnedQuantity || 0);
                if (maxAvailable <= 0) return null;

                return (
                  <div key={item.id} className="p-3 bg-slate-50 rounded-xl border border-slate-200 text-xs flex items-center justify-between gap-3">
                    <div className="flex-1">
                      <p className="font-bold text-slate-800">{item.medicineName}</p>
                      <p className="text-slate-400 font-mono text-[11px]">Batch: {item.batchNumber}</p>
                      <p className="text-[11px] text-slate-500">Price: {formatCurrency(item.unitPrice)} | Max return: {maxAvailable}</p>
                    </div>

                    <div className="flex items-center gap-2">
                      <label className="text-[11px] text-slate-500 font-medium">Return Qty:</label>
                      <input
                        type="number"
                        min="0"
                        max={maxAvailable}
                        value={returnItemQuantities[item.id] || 0}
                        onChange={(e) => handleReturnQtyChange(item.id, maxAvailable, e.target.value)}
                        className="w-16 px-2.5 py-1 rounded-lg border border-slate-300 text-center font-bold text-slate-800 text-xs focus:ring-teal-600 focus:outline-none"
                      />
                    </div>
                  </div>
                );
              })}
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">
                Return Reason *
              </label>
              <textarea
                rows="2"
                required
                value={returnReason}
                onChange={(e) => setReturnReason(e.target.value)}
                placeholder="Reason for return..."
                className="w-full px-3.5 py-2 rounded-xl border border-slate-200 text-xs focus:outline-none focus:ring-2 focus:ring-teal-600"
              />
            </div>

            <div className="p-3 bg-teal-50 rounded-xl border border-teal-200 flex justify-between items-center text-xs">
              <span className="font-semibold text-teal-900">Total Refund Amount:</span>
              <span className="text-base font-extrabold text-teal-800">{formatCurrency(estimatedRefund)}</span>
            </div>

            <div className="pt-4 border-t border-slate-100 flex items-center justify-end gap-3">
              <Button variant="secondary" onClick={() => setReturnModalOpen(false)} disabled={submittingReturn}>
                Cancel
              </Button>
              <Button type="submit" variant="danger" loading={submittingReturn} disabled={estimatedRefund <= 0}>
                Confirm Return & Refund
              </Button>
            </div>
          </form>
        )}
      </Modal>
    </div>
  );
}
