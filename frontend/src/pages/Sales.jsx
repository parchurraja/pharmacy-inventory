import React, { useState, useEffect, useRef } from 'react';
import {
  Search,
  ShoppingCart,
  Trash2,
  Plus,
  Minus,
  CheckCircle2,
  Printer,
  Sparkles,
  ArrowRight,
  Pill,
  User,
  AlertCircle,
  Clock,
  Layers,
} from 'lucide-react';
import { medicineService } from '../services/medicineService';
import { salesService } from '../services/salesService';
import { useToast } from '../context/ToastContext';
import { formatCurrency } from '../utils/currency';
import { formatDate, formatDateTime } from '../utils/date';
import { Button } from '../components/common/Button';
import { Modal } from '../components/common/Modal';

export function Sales() {
  const { success, error } = useToast();
  const searchInputRef = useRef(null);

  const [medicines, setMedicines] = useState([]);
  const [loadingMedicines, setLoadingMedicines] = useState(true);
  const [search, setSearch] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('ALL');

  // Cart state
  const [cart, setCart] = useState([]);
  const [customerName, setCustomerName] = useState('Walk-in Customer');
  const [processingSale, setProcessingSale] = useState(false);

  // Completed Invoice Modal state
  const [invoiceModalOpen, setInvoiceModalOpen] = useState(false);
  const [completedSale, setCompletedSale] = useState(null);

  // Load active medicines catalog
  const loadMedicines = async () => {
    try {
      setLoadingMedicines(true);
      const res = await medicineService.getAll({ size: 100, active: true });
      setMedicines(res?.content || []);
    } catch (err) {
      error('Failed to load medicines catalog');
    } finally {
      setLoadingMedicines(false);
    }
  };

  useEffect(() => {
    loadMedicines();
    if (searchInputRef.current) {
      searchInputRef.current.focus();
    }
  }, []);

  // Filter medicines
  const filteredMedicines = medicines.filter((m) => {
    const matchesSearch =
      m.name.toLowerCase().includes(search.toLowerCase()) ||
      (m.category && m.category.toLowerCase().includes(search.toLowerCase()));
    const matchesCategory = categoryFilter === 'ALL' || m.category === categoryFilter;
    return matchesSearch && matchesCategory;
  });

  // Barcode / Scanner / Quick-add on Enter key press
  const handleSearchKeyDown = (e) => {
    if (e.key === 'Enter' && filteredMedicines.length > 0) {
      e.preventDefault();
      handleAddToCart(filteredMedicines[0]);
    }
  };

  // Add medicine to cart (prevents duplicate entries by incrementing quantity)
  const handleAddToCart = (medicine) => {
    if (medicine.totalStock <= 0) {
      error(`Cannot add ${medicine.name}: Out of stock!`);
      return;
    }

    setCart((prevCart) => {
      const existingIdx = prevCart.findIndex((item) => item.medicineId === medicine.id);
      if (existingIdx !== -1) {
        const item = prevCart[existingIdx];
        if (item.quantity + 1 > medicine.totalStock) {
          error(`Cannot exceed total available stock (${medicine.totalStock} units) for ${medicine.name}`);
          return prevCart;
        }
        const updated = [...prevCart];
        updated[existingIdx] = {
          ...item,
          quantity: item.quantity + 1,
        };
        return updated;
      } else {
        return [
          ...prevCart,
          {
            medicineId: medicine.id,
            medicineName: medicine.name,
            totalStock: medicine.totalStock,
            category: medicine.category,
            quantity: 1,
            unitPrice: 10.0, // Default reference, actual batch price allocated by backend
          },
        ];
      }
    });
  };

  const handleUpdateQuantity = (medicineId, newQty) => {
    if (newQty <= 0) {
      handleRemoveItem(medicineId);
      return;
    }
    setCart((prevCart) =>
      prevCart.map((item) => {
        if (item.medicineId === medicineId) {
          if (newQty > item.totalStock) {
            error(`Cannot exceed available stock of ${item.totalStock} units`);
            return item;
          }
          return { ...item, quantity: newQty };
        }
        return item;
      })
    );
  };

  const handleRemoveItem = (medicineId) => {
    setCart((prevCart) => prevCart.filter((item) => item.medicineId !== medicineId));
  };

  const handleClearCart = () => {
    setCart([]);
  };

  // Calculate cart subtotal (estimated before server FEFO pricing confirmation)
  const cartEstimatedTotal = cart.reduce((sum, item) => sum + item.quantity * item.unitPrice, 0);

  // Complete checkout & call concurrency-safe FEFO API
  const handleCheckout = async () => {
    if (cart.length === 0) return;

    try {
      setProcessingSale(true);

      const payload = {
        customerName: customerName.trim() || 'Walk-in Customer',
        items: cart.map((item) => ({
          medicineId: item.medicineId,
          medicineName: item.medicineName,
          quantity: item.quantity,
        })),
      };

      const saleResponse = await salesService.createSale(payload);

      success(`Sale completed successfully! Invoice: ${saleResponse.invoiceNumber}`);
      setCompletedSale(saleResponse);
      setInvoiceModalOpen(true);
      setCart([]);
      setCustomerName('Walk-in Customer');

      // Refresh inventory to reflect latest batch stock
      loadMedicines();
    } catch (err) {
      error(err.message || 'Sale checkout failed. Please check stock levels.');
    } finally {
      setProcessingSale(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Page Title */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
        <div>
          <h1 className="text-xl sm:text-2xl font-bold text-slate-800 tracking-tight">Point of Sale (POS)</h1>
          <p className="text-xs sm:text-sm text-slate-500">
            Dispense medicine with concurrency-safe <strong>FEFO (First-Expired, First-Out)</strong> batch deduction.
          </p>
        </div>
      </div>

      {/* POS Grid: Catalog on Left, Cart on Right */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        {/* Left Pane: Medicine Selection */}
        <div className="lg:col-span-7 space-y-4">
          <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-sm space-y-3">
            {/* Search Input (Keyboard/Barcode friendly) */}
            <div className="relative">
              <Search className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none" />
              <input
                ref={searchInputRef}
                type="text"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                onKeyDown={handleSearchKeyDown}
                placeholder="Scan barcode or type medicine name... (Press Enter to add)"
                className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-teal-600 focus:border-transparent font-medium"
              />
            </div>

            {/* Category Pills */}
            <div className="flex items-center gap-1.5 overflow-x-auto pb-1 text-xs">
              {['ALL', 'Pain Relief', 'Antibiotics', 'Allergy', 'Diabetes', 'General'].map((cat) => (
                <button
                  key={cat}
                  onClick={() => setCategoryFilter(cat)}
                  className={`px-3 py-1.5 rounded-xl font-medium transition-colors shrink-0 ${
                    categoryFilter === cat
                      ? 'bg-teal-700 text-white'
                      : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                  }`}
                >
                  {cat === 'ALL' ? 'All Medicines' : cat}
                </button>
              ))}
            </div>
          </div>

          {/* Medicines Cards List */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 max-h-[600px] overflow-y-auto pr-1">
            {loadingMedicines ? (
              Array.from({ length: 6 }).map((_, i) => (
                <div key={i} className="p-4 bg-white rounded-2xl border border-slate-200 animate-pulse h-28" />
              ))
            ) : filteredMedicines.length === 0 ? (
              <div className="col-span-2 p-12 text-center bg-white rounded-2xl border border-slate-200 text-slate-400">
                No active medicines match your search.
              </div>
            ) : (
              filteredMedicines.map((med) => {
                const inCart = cart.find((i) => i.medicineId === med.id);
                const outOfStock = med.totalStock <= 0;

                return (
                  <div
                    key={med.id}
                    onClick={() => !outOfStock && handleAddToCart(med)}
                    className={`p-4 rounded-2xl border bg-white shadow-sm transition-all text-left flex flex-col justify-between ${
                      outOfStock
                        ? 'opacity-60 border-slate-200 cursor-not-allowed'
                        : 'border-slate-200/80 hover:border-teal-500 hover:shadow-md cursor-pointer group'
                    }`}
                  >
                    <div>
                      <div className="flex items-start justify-between gap-2">
                        <div className="w-7 h-7 rounded-lg bg-teal-50 text-teal-700 flex items-center justify-center shrink-0">
                          <Pill className="w-3.5 h-3.5" />
                        </div>
                        <span className="text-[10px] font-semibold uppercase px-2 py-0.5 rounded-full bg-slate-100 text-slate-600">
                          {med.category || 'General'}
                        </span>
                      </div>

                      <h4 className="font-bold text-slate-800 text-sm mt-2 group-hover:text-teal-700 transition-colors">
                        {med.name}
                      </h4>
                      <p className="text-[11px] text-slate-400 line-clamp-1 mt-0.5">
                        {med.description || 'Standard formulation'}
                      </p>
                    </div>

                    <div className="mt-4 pt-3 border-t border-slate-100 flex items-center justify-between">
                      <div>
                        <p className="text-[10px] text-slate-400 uppercase font-medium">Stock</p>
                        <p className={`text-xs font-bold ${outOfStock ? 'text-rose-600' : 'text-slate-700'}`}>
                          {outOfStock ? 'Out of stock' : `${med.totalStock} units`}
                        </p>
                      </div>

                      <div className="flex items-center gap-1.5">
                        {inCart && (
                          <span className="text-xs font-bold bg-teal-100 text-teal-800 px-2 py-0.5 rounded-lg">
                            {inCart.quantity} in cart
                          </span>
                        )}
                        <Button
                          variant={inCart ? 'primary' : 'secondary'}
                          size="sm"
                          disabled={outOfStock}
                          icon={Plus}
                        >
                          Add
                        </Button>
                      </div>
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>

        {/* Right Pane: Cart & Dispensing Summary */}
        <div className="lg:col-span-5 bg-white rounded-2xl border border-slate-200/80 shadow-sm p-6 sticky top-20 flex flex-col h-full">
          <div className="flex items-center justify-between pb-4 border-b border-slate-100">
            <div className="flex items-center gap-2">
              <ShoppingCart className="w-5 h-5 text-teal-700" />
              <h3 className="text-base font-bold text-slate-800">Current Dispense Cart</h3>
            </div>
            {cart.length > 0 && (
              <button
                onClick={handleClearCart}
                className="text-xs text-rose-600 hover:text-rose-700 font-medium"
              >
                Clear all
              </button>
            )}
          </div>

          {/* Customer Input */}
          <div className="mt-4">
            <label className="block text-xs font-semibold text-slate-700 mb-1">
              Customer / Patient Name
            </label>
            <div className="relative">
              <User className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                type="text"
                value={customerName}
                onChange={(e) => setCustomerName(e.target.value)}
                placeholder="Walk-in Customer"
                className="w-full pl-9 pr-3.5 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-teal-600"
              />
            </div>
          </div>

          {/* Cart Items List */}
          <div className="mt-4 flex-1 overflow-y-auto max-h-[320px] divide-y divide-slate-100">
            {cart.length === 0 ? (
              <div className="py-12 text-center">
                <ShoppingCart className="w-8 h-8 text-slate-300 mx-auto mb-2" />
                <p className="text-sm font-semibold text-slate-600">Cart is empty</p>
                <p className="text-xs text-slate-400 mt-1">Select medicines from the left catalog to dispense.</p>
              </div>
            ) : (
              cart.map((item) => (
                <div key={item.medicineId} className="py-3 flex items-center justify-between gap-3">
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-bold text-slate-800 truncate">{item.medicineName}</p>
                    <p className="text-[11px] text-teal-700 font-medium flex items-center gap-1">
                      <Sparkles className="w-3 h-3" />
                      FEFO: Earliest expiry batch allocated
                    </p>
                  </div>

                  {/* Quantity Stepper */}
                  <div className="flex items-center gap-1 bg-slate-100 rounded-xl p-1">
                    <button
                      onClick={() => handleUpdateQuantity(item.medicineId, item.quantity - 1)}
                      className="w-6 h-6 rounded-lg bg-white shadow-xs flex items-center justify-center text-slate-600 hover:text-slate-900"
                    >
                      <Minus className="w-3 h-3" />
                    </button>
                    <span className="w-8 text-center text-xs font-bold text-slate-800">
                      {item.quantity}
                    </span>
                    <button
                      onClick={() => handleUpdateQuantity(item.medicineId, item.quantity + 1)}
                      className="w-6 h-6 rounded-lg bg-white shadow-xs flex items-center justify-center text-slate-600 hover:text-slate-900"
                    >
                      <Plus className="w-3 h-3" />
                    </button>
                  </div>

                  <button
                    onClick={() => handleRemoveItem(item.medicineId)}
                    className="p-1.5 text-slate-400 hover:text-rose-600 rounded-lg"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              ))
            )}
          </div>

          {/* Cart Footer */}
          <div className="mt-4 pt-4 border-t border-slate-100 space-y-3">
            <div className="p-3 bg-teal-50 rounded-xl border border-teal-200/70 text-[11px] text-teal-900 leading-relaxed flex items-start gap-2">
              <Clock className="w-4 h-4 text-teal-700 shrink-0 mt-0.5" />
              <span>
                <strong>FEFO Guarantee:</strong> The backend server automatically prioritizes the batch expiring soonest using row-level pessimistic write locks.
              </span>
            </div>

            <Button
              variant="primary"
              size="lg"
              className="w-full justify-center"
              disabled={cart.length === 0 || processingSale}
              loading={processingSale}
              onClick={handleCheckout}
            >
              Complete Sale & Print Receipt
              <ArrowRight className="w-4 h-4 ml-1" />
            </Button>
          </div>
        </div>
      </div>

      {/* Invoice Receipt Modal (Printable) */}
      <Modal
        isOpen={invoiceModalOpen}
        onClose={() => setInvoiceModalOpen(false)}
        title="Dispensing Receipt & Invoice"
        subtitle="Sale successfully confirmed with actual batch allocations"
        maxWidth="max-w-xl"
      >
        {completedSale && (
          <div className="space-y-6">
            {/* Printable Receipt Card */}
            <div id="printable-invoice" className="p-6 bg-slate-50 rounded-2xl border border-slate-200 space-y-4 text-slate-800">
              <div className="flex justify-between items-start border-b border-slate-200 pb-4">
                <div>
                  <h3 className="font-extrabold text-lg text-teal-800">MediStock Pharmacy</h3>
                  <p className="text-xs text-slate-500">Licensed Operations & Dispensary</p>
                  <p className="text-xs text-slate-500 mt-1">Invoice: <strong>{completedSale.invoiceNumber}</strong></p>
                </div>
                <div className="text-right text-xs text-slate-500">
                  <p>Date: {formatDateTime(completedSale.saleDate)}</p>
                  <p>Customer: <strong>{completedSale.customerName}</strong></p>
                  <p>Dispensed by: <strong>{completedSale.soldBy}</strong></p>
                </div>
              </div>

              {/* Items & FEFO Traceability breakdown */}
              <div>
                <p className="text-xs font-semibold uppercase text-slate-500 mb-2">Itemized Batch Allocations</p>
                <div className="divide-y divide-slate-200 text-xs">
                  {completedSale.items.map((item, idx) => (
                    <div key={idx} className="py-2.5 flex justify-between items-center">
                      <div>
                        <p className="font-bold text-slate-800">{item.medicineName}</p>
                        <p className="text-[11px] text-slate-500 flex items-center gap-1.5 mt-0.5">
                          <Layers className="w-3 h-3 text-teal-600" />
                          Batch: <span className="font-mono font-semibold">{item.batchNumber}</span>
                          • Expiry: <span>{formatDate(item.expiryDate)}</span>
                        </p>
                      </div>
                      <div className="text-right">
                        <p className="font-medium text-slate-600">{item.quantity} × {formatCurrency(item.unitPrice)}</p>
                        <p className="font-bold text-slate-800">{formatCurrency(item.subtotal)}</p>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Total Summary */}
              <div className="border-t-2 border-slate-300 pt-3 flex justify-between items-center text-sm">
                <span className="font-bold text-slate-700">Total Billed Amount:</span>
                <span className="text-lg font-extrabold text-teal-800">
                  {formatCurrency(completedSale.totalAmount)}
                </span>
              </div>
            </div>

            {/* Modal Actions */}
            <div className="flex items-center justify-end gap-3 pt-4 border-t border-slate-100 no-print">
              <Button
                variant="secondary"
                icon={Printer}
                onClick={() => window.print()}
              >
                Print Receipt
              </Button>
              <Button
                variant="primary"
                onClick={() => setInvoiceModalOpen(false)}
              >
                Done
              </Button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}
