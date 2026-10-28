package com.setec.DAO;

import org.springframework.web.multipart.MultipartFile;

// All fields except id are optional: only the fields you send are updated.
public class PutProductDAO {
	private Integer id;
	private String name;
	private Double price;
	private Integer qty;
	private Integer categoryId;      // NEW
	private String description;      // NEW
	private Boolean available;       // NEW
	private MultipartFile imageFile;

	public Integer getId() { return id; }
	public void setId(Integer id) { this.id = id; }

	public String getName() { return name; }
	public void setName(String name) { this.name = name; }

	public Double getPrice() { return price; }
	public void setPrice(Double price) { this.price = price; }

	public Integer getQty() { return qty; }
	public void setQty(Integer qty) { this.qty = qty; }

	public Integer getCategoryId() { return categoryId; }
	public void setCategoryId(Integer categoryId) { this.categoryId = categoryId; }

	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = description; }

	public Boolean getAvailable() { return available; }
	public void setAvailable(Boolean available) { this.available = available; }

	public MultipartFile getImageFile() { return imageFile; }
	public void setImageFile(MultipartFile imageFile) { this.imageFile = imageFile; }
}