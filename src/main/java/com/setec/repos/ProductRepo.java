package com.setec.repos;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.setec.entities.Product;

@Repository
public interface ProductRepo extends JpaRepository<Product, Integer> {

	List<Product> findByName(String name);
	
	// added: Find all products belonging to a a category
//	List<Product> findByCategoryId(Integer categoryId);
	
	// Entity name is "tbl_product" because of @Entity(name = "tbl_product")
		@Query("select p from tbl_product p where p.category.id = :categoryId")
		List<Product> findByCategoryId(@Param("categoryId") Integer categoryId);
	 
		// NEW: used by GET /api/product/search?q=latte
		List<Product> findByNameContainingIgnoreCase(String keyword);

	
}
