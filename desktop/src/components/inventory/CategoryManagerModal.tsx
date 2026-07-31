import { useEffect, useState, type FormEvent } from "react";
import { api } from "../../lib/api";
import type { CategoryResponse } from "../../types";
import { ErrorNotice, SuccessNotice, TextInput } from "../FormControls";
import { InventoryModal } from "./InventoryModal";

function messageFrom(caught: unknown, fallback: string) {
  return caught instanceof Error ? caught.message : fallback;
}

export function CategoryManagerModal({
  accessToken,
  onClose,
  onChanged
}: {
  accessToken: string;
  onClose: () => void;
  onChanged: () => void;
}) {
  const [categories, setCategories] = useState<CategoryResponse[]>([]);
  const [newName, setNewName] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  async function load() {
    setLoading(true);
    try {
      setCategories(await api.listCategories(accessToken, true));
    } catch (caught) {
      setError(messageFrom(caught, "Categories could not be loaded."));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, [accessToken]);

  async function create(event: FormEvent) {
    event.preventDefault();
    setError("");
    setSuccess("");
    try {
      await api.createCategory(accessToken, newName);
      setNewName("");
      setSuccess("Category created.");
      await load();
      onChanged();
    } catch (caught) {
      setError(messageFrom(caught, "Category could not be created."));
    }
  }

  return (
    <InventoryModal
      title="Categories"
      description="Categories with active products cannot be deactivated."
      onClose={onClose}
      width="max-w-2xl"
    >
      <div className="space-y-5">
        {error && <ErrorNotice message={error} />}
        {success && <SuccessNotice message={success} />}
        <form onSubmit={create} className="flex items-end gap-3 rounded-xl bg-slate-50 p-4">
          <label className="min-w-0 flex-1">
            <span className="text-sm font-semibold text-slate-700">New category</span>
            <TextInput
              required
              maxLength={100}
              placeholder="Example: Grocery"
              value={newName}
              onChange={(event) => setNewName(event.target.value)}
            />
          </label>
          <button className="rounded-lg bg-indigo-700 px-4 py-2.5 text-sm font-bold text-white hover:bg-indigo-800">
            Add category
          </button>
        </form>

        <section className="overflow-hidden rounded-xl border border-slate-200">
          <div className="grid grid-cols-[1fr_110px_80px] bg-slate-100 px-4 py-3 text-xs font-bold uppercase tracking-wider text-slate-500">
            <span>Name</span><span>Status</span><span className="text-right">Action</span>
          </div>
          {loading ? (
            <p className="p-5 text-sm text-slate-500">Loading categories...</p>
          ) : categories.length === 0 ? (
            <p className="p-5 text-sm text-slate-500">No categories have been created.</p>
          ) : (
            <div className="divide-y divide-slate-100">
              {categories.map((category) => (
                <CategoryRow
                  key={`${category.id}-${category.version}`}
                  accessToken={accessToken}
                  category={category}
                  onSaved={async () => {
                    setSuccess("Category updated.");
                    setError("");
                    await load();
                    onChanged();
                  }}
                  onError={setError}
                />
              ))}
            </div>
          )}
        </section>
      </div>
    </InventoryModal>
  );
}

function CategoryRow({
  accessToken,
  category,
  onSaved,
  onError
}: {
  accessToken: string;
  category: CategoryResponse;
  onSaved: () => Promise<void>;
  onError: (message: string) => void;
}) {
  const [name, setName] = useState(category.name);
  const [active, setActive] = useState(category.active);
  const [saving, setSaving] = useState(false);

  async function save() {
    setSaving(true);
    onError("");
    try {
      await api.updateCategory(accessToken, category.id, {
        name,
        active,
        version: category.version
      });
      await onSaved();
    } catch (caught) {
      setActive(category.active);
      onError(messageFrom(caught, "Category could not be updated."));
    } finally {
      setSaving(false);
    }
  }

  const changed = name.trim() !== category.name || active !== category.active;

  return (
    <div className="grid grid-cols-[1fr_110px_80px] items-center gap-3 px-4 py-3">
      <TextInput
        className="mt-0"
        maxLength={100}
        value={name}
        onChange={(event) => setName(event.target.value)}
      />
      <label className="flex items-center gap-2 text-xs font-semibold text-slate-700">
        <input type="checkbox" checked={active} onChange={(event) => setActive(event.target.checked)} />
        {active ? "Active" : "Inactive"}
      </label>
      <button
        type="button"
        disabled={!changed || !name.trim() || saving}
        onClick={() => void save()}
        className="rounded-lg border border-slate-300 px-3 py-2 text-xs font-bold hover:bg-slate-50 disabled:opacity-40"
      >
        {saving ? "..." : "Save"}
      </button>
    </div>
  );
}
