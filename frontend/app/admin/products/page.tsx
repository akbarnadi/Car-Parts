"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { api } from "@/lib/api";
import { Product, ProductStatus } from "@/types/product";

const statusLabels: Record<ProductStatus, string> = {DRAFT:"پیش‌نویس",ACTIVE:"فعال",INACTIVE:"غیرفعال",DISCONTINUED:"توقف عرضه"};

export default function ProductsPage() {
  const [products,setProducts]=useState<Product[]>([]),[query,setQuery]=useState(""),[loading,setLoading]=useState(true),[error,setError]=useState("");
  const load=()=>api.products().then(setProducts).catch(e=>setError(e.message)).finally(()=>setLoading(false));
  useEffect(()=>{load()},[]);
  const filtered=useMemo(()=>{const q=query.toLowerCase().trim();return q?products.filter(p=>[p.productCode,p.realPartNumber,p.descriptionEn,p.descriptionFa].some(v=>v?.toLowerCase().includes(q))):products},[products,query]);
  async function remove(product:Product){if(!confirm(`محصول با کد ${product.productCode} حذف شود؟ سابقه آن حفظ خواهد شد.`))return;try{await api.deleteProduct(product.id);await load()}catch(e){setError(e instanceof Error?e.message:"حذف محصول انجام نشد.")}}
  return <div className="pageShell" dir="rtl"><div className="pageTitleRow"><div><span className="eyebrow">کاتالوگ</span><h1>محصولات</h1><p className="muted">ویرایش، قیمت معتبر، سابقه تغییرات و اعلان مشتری</p></div><Link className="button primary" href="/admin/products/new">+ محصول جدید</Link></div>
    <div className="toolbar"><input className="searchBox" value={query} onChange={e=>setQuery(e.target.value)} placeholder="کد محصول، شماره فنی یا نام را جستجو کنید…"/><span>{filtered.length} محصول</span></div>{error&&<div className="errorBanner">{error}</div>}
    {loading?<div className="loadingCard">در حال بارگذاری محصولات…</div>:<div className="productTableWrap"><table className="productTable"><thead><tr><th>محصول</th><th>شماره فنی</th><th>وضعیت</th><th>مشخصات فیزیکی</th><th>تصاویر</th><th>عملیات</th></tr></thead><tbody>{filtered.map(product=><tr key={product.id}><td><div className="productIdentity">{product.images[0]?.imageUrl?<img src={product.images[0].imageUrl} alt=""/>:<span className="imagePlaceholder">CP</span>}<div><code dir="ltr">{product.productCode}</code><strong>{product.descriptionFa}</strong><small dir="ltr">{product.descriptionEn}</small></div></div></td><td><strong dir="ltr">{product.realPartNumber}</strong></td><td><span className={`status status-${product.status.toLowerCase()}`}>{statusLabels[product.status]}</span></td><td>{product.physicalProfile?<span>{product.physicalProfile.weightKg??"—"} کیلوگرم<br/><small dir="ltr">{product.physicalProfile.lengthCm??"—"}×{product.physicalProfile.widthCm??"—"}×{product.physicalProfile.heightCm??"—"} cm</small></span>:<span className="muted">ثبت نشده</span>}</td><td>{product.images.length}/10</td><td><div className="rowActions"><Link href={`/admin/products/${product.id}/operations`}>قیمت / سابقه</Link><Link href={`/admin/products/${product.id}/edit`}>ویرایش</Link><button onClick={()=>remove(product)}>حذف</button></div></td></tr>)}{!filtered.length&&<tr><td colSpan={6}><div className="emptyInset">محصولی با این جستجو پیدا نشد.</div></td></tr>}</tbody></table></div>}
  </div>
}
