"use client";

import {FormEvent,useEffect,useMemo,useState} from "react";
import {api} from "@/lib/api";
import {lookupConfig} from "@/lib/lookupConfig";
import {LookupAdminRow} from "@/types/platform";
import {LookupOption} from "@/types/product";

export default function LookupEditorModal({
  type,row,vehicleTypes=[],fixedVehicleTypeId,onClose,onSaved
}:{
  type:string;
  row?:LookupAdminRow|null;
  vehicleTypes?:LookupOption[];
  fixedVehicleTypeId?:string;
  onClose:()=>void;
  onSaved:(r:LookupAdminRow)=>void;
}){
  const cfg=lookupConfig[type];
  const [knownRows,setKnownRows]=useState<LookupAdminRow[]>([]);
  const [form,setForm]=useState<any>({
    code:"",
    descriptionFa:"",
    descriptionEn:"",
    active:true,
    vehicleTypeCodeId:fixedVehicleTypeId??"",
    designCountry:"",
    manufacturingCountry:"",
    weightKg:"",
    lengthCm:"",
    widthCm:"",
    heightCm:""
  });
  const [busy,setBusy]=useState(false),[error,setError]=useState("");

  useEffect(()=>{
    if(row){
      setForm({
        code:row.code,
        descriptionFa:row.descriptionFa,
        descriptionEn:row.descriptionEn,
        active:row.active,
        vehicleTypeCodeId:row.vehicleTypeCodeId??fixedVehicleTypeId??"",
        designCountry:row.designCountry??"",
        manufacturingCountry:row.manufacturingCountry??"",
        weightKg:row.weightKg??"",
        lengthCm:row.lengthCm??"",
        widthCm:row.widthCm??"",
        heightCm:row.heightCm??""
      });
      return;
    }
    api.lookupRows(type).then(setKnownRows).catch(()=>setKnownRows([]));
  },[row,type,fixedVehicleTypeId]);

  const scopedVehicleType=form.vehicleTypeCodeId||fixedVehicleTypeId||"";
  const nextCode=useMemo(()=>{
    if(row)return row.code;
    if(type==="vehicle-brands"&&!scopedVehicleType)return null;
    const source=type==="vehicle-brands"
      ? knownRows.filter(r=>r.vehicleTypeCodeId===scopedVehicleType)
      : knownRows;
    return source.reduce((max,r)=>Math.max(max,r.code),0)+1;
  },[row,type,knownRows,scopedVehicleType]);

  async function submit(e:FormEvent){
    e.preventDefault();setBusy(true);setError("");
    try{
      const body={
        ...form,
        code:row?Number(form.code):null,
        vehicleTypeCodeId:form.vehicleTypeCodeId||fixedVehicleTypeId||null,
        weightKg:form.weightKg===""?null:Number(form.weightKg),
        lengthCm:form.lengthCm===""?null:Number(form.lengthCm),
        widthCm:form.widthCm===""?null:Number(form.widthCm),
        heightCm:form.heightCm===""?null:Number(form.heightCm)
      };
      const saved=row?await api.updateLookup(type,row.id,body):await api.createLookup(type,body);
      onSaved(saved);
    }catch(x){setError(x instanceof Error?x.message:"ذخیره انجام نشد")}
    finally{setBusy(false)}
  }

  return <div className="modalBackdrop" onMouseDown={onClose}>
    <div className="modal" role="dialog" aria-modal="true" aria-label={`${row?"ویرایش":"افزودن"} ${cfg?.singular}`} onMouseDown={e=>e.stopPropagation()}>
      <div className="modalHeader"><h3>{row?"ویرایش":"افزودن"} {cfg?.singular}</h3><button type="button" aria-label="بستن" onClick={onClose}>×</button></div>
      <form onSubmit={submit}>
        <div className="modalBody">
          {!row&&<div className="autoCodeBadge"><span>کد بعدی</span><strong>{nextCode==null?"—":String(nextCode).padStart(cfg?.codeWidth??1,"0")}</strong><small>{type==="vehicle-brands"&&!scopedVehicleType?"ابتدا نوع خودرو را انتخاب کنید.":"کد هنگام ذخیره به‌صورت خودکار از ادامه کدهای موجود تخصیص می‌یابد."}</small></div>}

          {row&&<div className="fieldGrid"><label className="field"><span>کد</span><input dir="ltr" type="number" min="1" required value={form.code} onChange={e=>setForm({...form,code:e.target.value})}/></label></div>}

          {type==="vehicle-brands"&&<div className="fieldGrid"><label className="field"><span>نوع خودرو</span><select required disabled={!!fixedVehicleTypeId} value={form.vehicleTypeCodeId} onChange={e=>setForm({...form,vehicleTypeCodeId:e.target.value})}><option value="">انتخاب کنید</option>{vehicleTypes.map(v=><option key={v.id} value={v.id}>{String(v.code).padStart(2,"0")} · {v.descriptionFa}</option>)}</select></label></div>}

          <div className="fieldGrid two"><label className="field"><span>نام فارسی</span><input required value={form.descriptionFa} onChange={e=>setForm({...form,descriptionFa:e.target.value})}/></label><label className="field"><span>نام انگلیسی</span><input dir="ltr" required value={form.descriptionEn} onChange={e=>setForm({...form,descriptionEn:e.target.value})}/></label></div>

          {type==="part-brands"&&<div className="fieldGrid two"><label className="field"><span>کشور طراحی</span><input value={form.designCountry} onChange={e=>setForm({...form,designCountry:e.target.value})}/></label><label className="field"><span>کشور تولید</span><input value={form.manufacturingCountry} onChange={e=>setForm({...form,manufacturingCountry:e.target.value})}/></label></div>}

          {type==="physical-codes"&&<div className="fieldGrid four"><label className="field"><span>وزن kg</span><input dir="ltr" type="number" step="0.001" value={form.weightKg} onChange={e=>setForm({...form,weightKg:e.target.value})}/></label><label className="field"><span>طول cm</span><input dir="ltr" type="number" step="0.01" value={form.lengthCm} onChange={e=>setForm({...form,lengthCm:e.target.value})}/></label><label className="field"><span>عرض cm</span><input dir="ltr" type="number" step="0.01" value={form.widthCm} onChange={e=>setForm({...form,widthCm:e.target.value})}/></label><label className="field"><span>ارتفاع cm</span><input dir="ltr" type="number" step="0.01" value={form.heightCm} onChange={e=>setForm({...form,heightCm:e.target.value})}/></label></div>}

          <label style={{display:"flex",gap:8,alignItems:"center",marginTop:18}}><input type="checkbox" checked={form.active!==false} onChange={e=>setForm({...form,active:e.target.checked})}/> فعال برای انتخاب‌های جدید</label>
          {error&&<div className="errorBanner">{error}</div>}
        </div>
        <div className="modalFooter"><button className="primaryButton" disabled={busy}>{busy?"در حال ذخیره…":"ذخیره"}</button><button className="secondaryButton" type="button" onClick={onClose}>انصراف</button></div>
      </form>
    </div>
  </div>;
}
