export type ProductStatus = "DRAFT" | "ACTIVE" | "INACTIVE" | "DISCONTINUED";

export interface LookupOption {
  id: string;
  code: number;
  descriptionFa: string;
  descriptionEn: string;
}

export interface VehicleBrandOption extends LookupOption {
  vehicleTypeCodeId: string;
}

export interface PartBrandOption extends LookupOption {
  designCountry?: string | null;
  manufacturingCountry?: string | null;
}

export interface PhysicalProfile extends LookupOption {
  weightKg?: number | null;
  lengthCm?: number | null;
  widthCm?: number | null;
  heightCm?: number | null;
}

export interface Lookups {
  vehicleTypes: LookupOption[];
  vehicleBrands: VehicleBrandOption[];
  partBrands: PartBrandOption[];
  packaging: LookupOption[];
  qualities: LookupOption[];
  productGroups: LookupOption[];
  units: LookupOption[];
  physicalCodes: PhysicalProfile[];
}

export interface ProductImage {
  imageUrl: string;
  altText?: string | null;
  sortOrder: number;
}

export interface Product {
  id: string;
  descriptionFa: string;
  descriptionEn: string;
  realPartNumber: string;
  productCode: string;
  productSerial: number;
  productGroupCodeId: string;
  unitCodeId: string;
  physicalCodeId?: string | null;
  physicalProfile?: PhysicalProfile | null;
  code1?: string | null;
  code2?: string | null;
  status: ProductStatus;
  subject1?: string | null;
  subject2?: string | null;
  subject3?: string | null;
  images: ProductImage[];
  createdAt: string;
  updatedAt: string;
}
